package io.ionic.keyboard;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.content.pm.PackageItemInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodInfo;

// import additionally required classes for calculating screen height
import android.view.Display;
import android.graphics.Point;
import android.os.Build;
import android.os.IBinder;
import android.widget.FrameLayout;
import android.provider.Settings;

import java.util.List;

public class CDVIonicKeyboard extends CordovaPlugin {
    private OnGlobalLayoutListener list;
    private View rootView;
    private View mChildOfContent;
    private int usableHeightPrevious;
    private FrameLayout.LayoutParams frameLayoutParams;

    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
    }

    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if ("hide".equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    //http://stackoverflow.com/a/7696791/1091751
                    InputMethodManager inputManager = (InputMethodManager) cordova.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    View v = cordova.getActivity().getCurrentFocus();

                    if (v == null) {
                        callbackContext.error("No current focus");
                    } else {
                        inputManager.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                        callbackContext.success(); // Thread-safe.
                    }
                }
            });
            return true;
        }
        if ("show".equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    InputMethodManager imeManager = ((InputMethodManager) cordova.getActivity().getSystemService(Context.INPUT_METHOD_SERVICE));
                    List<InputMethodInfo> InputMethods = imeManager.getEnabledInputMethodList();
                    final int N = InputMethods.size();
                    for (int i = 0; i < N; i++) {
                        InputMethodInfo imi = InputMethods.get(i);
                        if (imi.getId().equals(Settings.Secure.getString(cordova.getActivity().getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD))) {
                            if ((imi.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                                if (!imi.getPackageName().toString().toLowerCase().contains("bradesco") && 
                                    !imi.getPackageName().toString().toLowerCase().contains("gboard")
                                ) {
                                    imeManager.showInputMethodPicker();
                                }
                                break;
                            }
                        }
                    }
                    callbackContext.success(); // Thread-safe.
                }
            });
            return true;
        }

        if ("init".equals(action)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                	//calculate density-independent pixels (dp)
                    //http://developer.android.com/guide/practices/screens_support.html
                    DisplayMetrics dm = new DisplayMetrics();
                    cordova.getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
                    final float density = dm.density;

                    //http://stackoverflow.com/a/4737265/1091751 detect if keyboard is showing
                    FrameLayout content = (FrameLayout) cordova.getActivity().findViewById(android.R.id.content);
                    rootView = content.getRootView();
                    list = new OnGlobalLayoutListener() {
                        int previousHeightDiff = 0;
                        @Override
                        public void onGlobalLayout() {
                            boolean resize = preferences.getBoolean("resizeOnFullScreen", false);
                            if (resize) {
                                possiblyResizeChildOfContent();
                            }
                            Rect r = new Rect();
                            //r will be populated with the coordinates of your view that area still visible.
                            rootView.getWindowVisibleDisplayFrame(r);

                            PluginResult result;

                            // cache properties for later use
                            int rootViewHeight = rootView.getRootView().getHeight();
                            int resultBottom = r.bottom;

                            // calculate screen height differently for android versions >= 21: Lollipop 5.x, Marshmallow 6.x
                            //http://stackoverflow.com/a/29257533/3642890 beware of nexus 5
                            int screenHeight;

                            if (Build.VERSION.SDK_INT >= 21) {
                                Display display = cordova.getActivity().getWindowManager().getDefaultDisplay();
                                Point size = new Point();
                                display.getSize(size);
                                screenHeight = size.y;
                            } else {
                                screenHeight = rootViewHeight;
                            }

                            int heightDiff = screenHeight - resultBottom;

                            int pixelHeightDiff = (int)(heightDiff / density);
                            if (pixelHeightDiff > 100 && pixelHeightDiff != previousHeightDiff) { // if more than 100 pixels, its probably a keyboard...
                                String msg = "S" + Integer.toString(pixelHeightDiff);
                                result = new PluginResult(PluginResult.Status.OK, msg);
                                result.setKeepCallback(true);
                                callbackContext.sendPluginResult(result);
                            }
                            else if ( pixelHeightDiff != previousHeightDiff && ( previousHeightDiff - pixelHeightDiff ) > 100 ){
                            	String msg = "H";
                                result = new PluginResult(PluginResult.Status.OK, msg);
                                result.setKeepCallback(true);
                                callbackContext.sendPluginResult(result);
                            }
                            previousHeightDiff = pixelHeightDiff;
                        }

                        private void possiblyResizeChildOfContent() {
                            int usableHeightNow = computeUsableHeight();
                            if (usableHeightNow != usableHeightPrevious) {
                                int usableHeightSansKeyboard = mChildOfContent.getRootView().getHeight();
                                int heightDifference = usableHeightSansKeyboard - usableHeightNow;
                                if (heightDifference > (usableHeightSansKeyboard/4)) {
                                    frameLayoutParams.height = usableHeightSansKeyboard - heightDifference;
                                } else {
                                    frameLayoutParams.height = usableHeightSansKeyboard;
                                }
                                mChildOfContent.requestLayout();
                                usableHeightPrevious = usableHeightNow;
                            }
                        }

                        private int computeUsableHeight() {
                            Rect r = new Rect();
                            mChildOfContent.getWindowVisibleDisplayFrame(r);
                            return (r.bottom - r.top);
                        }
                    };

                    mChildOfContent = content.getChildAt(0);
                    rootView.getViewTreeObserver().addOnGlobalLayoutListener(list);
                    frameLayoutParams = (FrameLayout.LayoutParams) mChildOfContent.getLayoutParams();
                    PluginResult dataResult = new PluginResult(PluginResult.Status.OK);
                    dataResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(dataResult);
                }
            });
            return true;
        }
        return false;  // Returning false results in a "MethodNotFound" error.
    }

    @Override
    public void onDestroy() {
        rootView.getViewTreeObserver().removeOnGlobalLayoutListener(list);
    }

}
