/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.mac;

import com.apple.eawt.*;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.CustomProtocolHandler;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.EventListener;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.ui.mac.foundation.Foundation.invoke;

/**
 * User: spLeaner
 */
public class MacMainFrameDecorator extends IdeFrameDecorator implements UISettingsListener {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.mac.MacMainFrameDecorator");

  private final FullscreenQueue<Runnable> myFullscreenQueue = new FullscreenQueue<>();

  private final EventDispatcher<FSListener> myDispatcher = EventDispatcher.create(FSListener.class);

  private interface FSListener extends FullScreenListener, EventListener {}
  private static class FSAdapter extends FullScreenAdapter implements FSListener {}

  private static class FullscreenQueue <T extends Runnable> {
    private boolean waitingForAppKit = false;
    private LinkedList<Runnable> queueModel = new LinkedList<>();

    synchronized void runOrEnqueue (final T runnable) {
      if (waitingForAppKit) {
        enqueue(runnable);
      }
      else {
        ApplicationManager.getApplication().invokeLater(runnable);
        waitingForAppKit = true;
      }
    }

    synchronized private void enqueue (final T runnable) {
      queueModel.add(runnable);
    }

    synchronized void runFromQueue () {
      if (!queueModel.isEmpty()) {
        queueModel.remove().run();
        waitingForAppKit = true;
      } else {
        waitingForAppKit = false;
      }
    }
  }


  // Fullscreen listener delivers event too late,
  // so we use method swizzling here
  private final Callback windowWillEnterFullScreenCallBack = new Callback() {
    public void callback(ID self,
                         ID nsNotification)
    {
      invoke(self, "oldWindowWillEnterFullScreen:", nsNotification);
      enterFullscreen();
    }
  };

  private void enterFullscreen() {
    myInFullScreen = true;
    myFrame.storeFullScreenStateIfNeeded(true);
    myFullscreenQueue.runFromQueue();
  }

  private final Callback windowWillExitFullScreenCallBack = new Callback() {
    public void callback(ID self,
                         ID nsNotification)
    {
      invoke(self, "oldWindowWillExitFullScreen:", nsNotification);
      exitFullscreen();
    }
  };

  private void exitFullscreen() {
    myInFullScreen = false;
    myFrame.storeFullScreenStateIfNeeded(false);

    JRootPane rootPane = myFrame.getRootPane();
    if (rootPane != null) rootPane.putClientProperty(FULL_SCREEN, null);
    myFullscreenQueue.runFromQueue();
  }

  public static final String FULL_SCREEN = "Idea.Is.In.FullScreen.Mode.Now";
  private static boolean HAS_FULLSCREEN_UTILITIES;

  private static Method requestToggleFullScreenMethod;

  static {
    try {
      Class.forName("com.apple.eawt.FullScreenUtilities");
      requestToggleFullScreenMethod = Application.class.getMethod("requestToggleFullScreen", Window.class);
      HAS_FULLSCREEN_UTILITIES = true;
    } catch (Exception e) {
      HAS_FULLSCREEN_UTILITIES = false;
    }
  }
  public static final boolean FULL_SCREEN_AVAILABLE = SystemInfo.isJavaVersionAtLeast("1.6.0_29") && HAS_FULLSCREEN_UTILITIES;

  private static boolean SHOWN = false;

  private static Callback SET_VISIBLE_CALLBACK = new Callback() {
    public void callback(ID caller, ID selector, ID value) {
      SHOWN = value.intValue() == 1;
      SwingUtilities.invokeLater(CURRENT_SETTER);
    }
  };

  private static Callback IS_VISIBLE = new Callback() {
    public boolean callback(ID caller) {
      return SHOWN;
    }
  };

  private static AtomicInteger UNIQUE_COUNTER = new AtomicInteger(0);

  public static final Runnable TOOLBAR_SETTER = () -> {
    final UISettings settings = UISettings.getInstance();
    settings.SHOW_MAIN_TOOLBAR = SHOWN;
    settings.fireUISettingsChanged();
  };

  public static final Runnable NAVBAR_SETTER = () -> {
    final UISettings settings = UISettings.getInstance();
    settings.SHOW_NAVIGATION_BAR = SHOWN;
    settings.fireUISettingsChanged();
  };

  @SuppressWarnings("Convert2Lambda")
  public static final Function<Object, Boolean> NAVBAR_GETTER = new Function<Object, Boolean>() {
    @Override
    public Boolean fun(Object o) {
      return UISettings.getInstance().SHOW_NAVIGATION_BAR;
    }
  };

  @SuppressWarnings("Convert2Lambda")
  public static final Function<Object, Boolean> TOOLBAR_GETTER = new Function<Object, Boolean>() {
    @Override
    public Boolean fun(Object o) {
      return UISettings.getInstance().SHOW_MAIN_TOOLBAR;
    }
  };

  private static Runnable CURRENT_SETTER = null;
  private static Function<Object, Boolean> CURRENT_GETTER = null;
  private static CustomProtocolHandler ourProtocolHandler = null;

  private boolean myInFullScreen;

  public MacMainFrameDecorator(@NotNull final IdeFrameImpl frame, final boolean navBar) {
    super(frame);

    if (CURRENT_SETTER == null) {
      CURRENT_SETTER = navBar ? NAVBAR_SETTER : TOOLBAR_SETTER;
      CURRENT_GETTER = navBar ? NAVBAR_GETTER : TOOLBAR_GETTER;
      SHOWN = CURRENT_GETTER.fun(null);
    }

    UISettings.getInstance().addUISettingsListener(this, this);

    final ID pool = invoke("NSAutoreleasePool", "new");

    int v = UNIQUE_COUNTER.incrementAndGet();

    try {
      if (SystemInfo.isMacOSLion) {
        if (!FULL_SCREEN_AVAILABLE) return;

        FullScreenUtilities.setWindowCanFullScreen(frame, true);
        // Native fullscreen listener can be set only once
        FullScreenUtilities.addFullScreenListenerTo(frame, new FullScreenListener() {
          @Override
          public void windowEnteringFullScreen(AppEvent.FullScreenEvent event) {
            myDispatcher.getMulticaster().windowEnteringFullScreen(event);
          }

          @Override
          public void windowEnteredFullScreen(AppEvent.FullScreenEvent event) {
            myDispatcher.getMulticaster().windowEnteredFullScreen(event);
          }

          @Override
          public void windowExitingFullScreen(AppEvent.FullScreenEvent event) {
            myDispatcher.getMulticaster().windowExitingFullScreen(event);
          }

          @Override
          public void windowExitedFullScreen(AppEvent.FullScreenEvent event) {
            myDispatcher.getMulticaster().windowExitedFullScreen(event);
          }
        });
        myDispatcher.addListener(new FSAdapter() {
          @Override
          public void windowEnteredFullScreen(AppEvent.FullScreenEvent event) {
            // We can get the notification when the frame has been disposed
            JRootPane rootPane = frame.getRootPane();
            if (rootPane != null) rootPane.putClientProperty(FULL_SCREEN, Boolean.TRUE);
            enterFullscreen();
            myFrame.validate();
          }

          @Override
          public void windowExitedFullScreen(AppEvent.FullScreenEvent event) {
            // We can get the notification when the frame has been disposed
            if (myFrame == null/* || ORACLE_BUG_ID_8003173*/) return;
            exitFullscreen();
            myFrame.validate();
          }
        });
      }
      else {
        final ID window = MacUtil.findWindowForTitle(frame.getTitle());
        if (window == null) return;

        // toggle toolbar
        String className = "IdeaToolbar" + v;
        final ID ownToolbar = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSToolbar"), className);
        Foundation.registerObjcClassPair(ownToolbar);

        final ID toolbar = invoke(invoke(className, "alloc"), "initWithIdentifier:", Foundation.nsString(className));
        Foundation.cfRetain(toolbar);

        invoke(toolbar, "setVisible:", 0); // hide native toolbar by default

        Foundation.addMethod(ownToolbar, Foundation.createSelector("setVisible:"), SET_VISIBLE_CALLBACK, "v*");
        Foundation.addMethod(ownToolbar, Foundation.createSelector("isVisible"), IS_VISIBLE, "B*");

        Foundation.executeOnMainThread(() -> {
          invoke(window, "setToolbar:", toolbar);
          invoke(window, "setShowsToolbarButton:", 1);
        }, true, true);
      }
    }
    finally {
      invoke(pool, "release");
    }

    // extract to static method for exclude this from OpenURIHandler() {} anonymous class
    createProtocolHandler();
  }

  private static void createProtocolHandler() {
    if (ourProtocolHandler == null) {
      // install uri handler
      final ID mainBundle = invoke("NSBundle", "mainBundle");
      final ID urlTypes = invoke(mainBundle, "objectForInfoDictionaryKey:", Foundation.nsString("CFBundleURLTypes"));
      final ApplicationInfoEx info = ApplicationInfoImpl.getShadowInstance();
      final BuildNumber build = info != null ? info.getBuild() : null;
      if (urlTypes.equals(ID.NIL) && build != null && !build.isSnapshot()) {
        LOG.warn("no url bundle present. \n" +
                 "To use platform protocol handler to open external links specify required protocols in the mac app layout section of the build file\n" +
                 "Example: args.urlSchemes = [\"your-protocol\"] will handle following links: your-protocol://open?file=file&line=line");
        return;
      }
      ourProtocolHandler = new CustomProtocolHandler();
      Application.getApplication().setOpenURIHandler(new OpenURIHandler() {
        @Override
        public void openURI(AppEvent.OpenURIEvent event) {
          ourProtocolHandler.openLink(event.getURI());
        }
      });
    }
  }

  private void replaceNativeFullscreenListenerCallback() {
    ID awtWindow = Foundation.getObjcClass("AWTWindow");

    Pointer windowWillEnterFullScreenMethod = Foundation.createSelector("windowWillEnterFullScreen:");
    ID originalWindowWillEnterFullScreen = Foundation.class_replaceMethod(awtWindow, windowWillEnterFullScreenMethod,
                                                                          windowWillEnterFullScreenCallBack, "v@::@");

    Foundation.addMethodByID(awtWindow, Foundation.createSelector("oldWindowWillEnterFullScreen:"),
                             originalWindowWillEnterFullScreen, "v@::@");

    Pointer  windowWillExitFullScreenMethod = Foundation.createSelector("windowWillExitFullScreen:");
    ID originalWindowWillExitFullScreen = Foundation.class_replaceMethod(awtWindow, windowWillExitFullScreenMethod,
                                                                         windowWillExitFullScreenCallBack, "v@::@");

    Foundation.addMethodByID(awtWindow, Foundation.createSelector("oldWindowWillExitFullScreen:"),
                             originalWindowWillExitFullScreen, "v@::@");
  }

  @Override
  public void uiSettingsChanged(final UISettings source) {
    if (CURRENT_GETTER != null) {
      SHOWN = CURRENT_GETTER.fun(null);
    }
  }

  @Override
  public boolean isInFullScreen() {
    return myInFullScreen;
  }

  @Override
  public ActionCallback toggleFullScreen(final boolean state) {
    if (!SystemInfo.isMacOSLion || myFrame == null || myInFullScreen == state) return ActionCallback.REJECTED;
    final ActionCallback callback = new ActionCallback();
    myDispatcher.addListener(new FSAdapter() {
      @Override
      public void windowExitedFullScreen(AppEvent.FullScreenEvent event) {
        callback.setDone();
        myDispatcher.removeListener(this);
      }

      @Override
      public void windowEnteredFullScreen(AppEvent.FullScreenEvent event) {
        callback.setDone();
        myDispatcher.removeListener(this);
      }
    });

    myFullscreenQueue.runOrEnqueue(() -> toggleFullScreenNow());
    return callback;
  }

  public void toggleFullScreenNow() {
    try {
      requestToggleFullScreenMethod.invoke(Application.getApplication(), myFrame);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }
}
