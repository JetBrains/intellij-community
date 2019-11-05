// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.apple.eawt.*;
import com.intellij.ide.ActiveWindowsWatcher;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.ui.CustomProtocolHandler;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Callback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.EventListener;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.intellij.ui.mac.foundation.Foundation.invoke;

public final class MacMainFrameDecorator extends IdeFrameDecorator {
  private static final Logger LOG = Logger.getInstance(MacMainFrameDecorator.class);

  private final FullscreenQueue<Runnable> myFullscreenQueue = new FullscreenQueue<>();

  private final EventDispatcher<FSListener> myDispatcher = EventDispatcher.create(FSListener.class);

  private interface FSListener extends FullScreenListener, EventListener {}
  private static class FSAdapter extends FullScreenAdapter implements FSListener {}

  private static class FullscreenQueue <T extends Runnable> {
    private boolean waitingForAppKit = false;
    private final LinkedList<Runnable> queueModel = new LinkedList<>();

    synchronized void runOrEnqueue(@NotNull T runnable) {
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

  private void enterFullscreen() {
    myInFullScreen = true;
    storeFullScreenStateIfNeeded();
    myFullscreenQueue.runFromQueue();
  }

  private void exitFullscreen() {
    myInFullScreen = false;
    storeFullScreenStateIfNeeded();

    JRootPane rootPane = myFrame.getRootPane();
    if (rootPane != null) rootPane.putClientProperty(FULL_SCREEN, null);
    myFullscreenQueue.runFromQueue();
  }

  private void storeFullScreenStateIfNeeded() {
    // todo should we really check that frame has not null project as it was implemented previously?
    myFrame.doLayout();
  }

  public static final String FULL_SCREEN = "Idea.Is.In.FullScreen.Mode.Now";
  private static boolean HAS_FULLSCREEN_UTILITIES;

  private static Method requestToggleFullScreenMethod;
  private static Method enterFullScreenMethod;
  private static Method leaveFullScreenMethod;

  static {
    try {
      Class.forName("com.apple.eawt.FullScreenUtilities");
      //noinspection JavaReflectionMemberAccess
      enterFullScreenMethod = Application.class.getMethod("requestEnterFullScreen", Window.class);
      leaveFullScreenMethod = Application.class.getMethod("requestLeaveFullScreen", Window.class);
      HAS_FULLSCREEN_UTILITIES = true;
    }
    catch (Exception e) {
      HAS_FULLSCREEN_UTILITIES = false;
    }
    // temporary solution for the old Runtime
    if (!HAS_FULLSCREEN_UTILITIES) {
      try {
        Class.forName("com.apple.eawt.FullScreenUtilities");
        //noinspection JavaReflectionMemberAccess
        requestToggleFullScreenMethod = Application.class.getMethod("requestToggleFullScreen", Window.class);
        HAS_FULLSCREEN_UTILITIES = true;
      }
      catch (Exception e) {
        HAS_FULLSCREEN_UTILITIES = false;
      }
    }
  }

  public static final boolean FULL_SCREEN_AVAILABLE = HAS_FULLSCREEN_UTILITIES;

  private static boolean SHOWN = false;

  private static final Callback SET_VISIBLE_CALLBACK = new Callback() {
    @SuppressWarnings("unused")
    public void callback(ID caller, ID selector, ID value) {
      SHOWN = value.intValue() == 1;
      SwingUtilities.invokeLater(CURRENT_SETTER);
    }
  };

  private static final Callback IS_VISIBLE = new Callback() {
    @SuppressWarnings("unused")
    public boolean callback(ID caller) {
      return SHOWN;
    }
  };

  private static final AtomicInteger UNIQUE_COUNTER = new AtomicInteger(0);

  public static final Runnable TOOLBAR_SETTER = () -> {
    final UISettings settings = UISettings.getInstance();
    settings.setShowMainToolbar(SHOWN);
    settings.fireUISettingsChanged();
  };

  public static final Runnable NAVBAR_SETTER = () -> {
    final UISettings settings = UISettings.getInstance();
    settings.setShowNavigationBar(SHOWN);
    settings.fireUISettingsChanged();
  };

  public static final Supplier<Boolean> NAV_BAR_GETTER = () -> UISettings.getInstance().getShowNavigationBar();

  public static final Supplier<Boolean> TOOLBAR_GETTER = () -> UISettings.getInstance().getShowMainToolbar();

  private static Runnable CURRENT_SETTER = null;
  private static Supplier<Boolean> CURRENT_GETTER = null;
  private static CustomProtocolHandler ourProtocolHandler = null;

  private boolean myInFullScreen;

  public MacMainFrameDecorator(@NotNull JFrame frame, boolean navBar, @NotNull Disposable parentDisposable) {
    super(frame);

    if (CURRENT_SETTER == null) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      CURRENT_SETTER = navBar ? NAVBAR_SETTER : TOOLBAR_SETTER;
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      CURRENT_GETTER = navBar ? NAV_BAR_GETTER : TOOLBAR_GETTER;
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      SHOWN = CURRENT_GETTER.get();
    }

    //noinspection Convert2Lambda
    ApplicationManager.getApplication().getMessageBus().connect(parentDisposable).subscribe(UISettingsListener.TOPIC, new UISettingsListener() {
      @Override
      public void uiSettingsChanged(final UISettings uiSettings) {
        if (CURRENT_GETTER != null) {
          //noinspection AssignmentToStaticFieldFromInstanceMethod
          SHOWN = CURRENT_GETTER.get();
        }
      }
    });

    final ID pool = invoke("NSAutoreleasePool", "new");

    int v = UNIQUE_COUNTER.incrementAndGet();

    try {
      if (SystemInfo.isMacOSLion) {
        if (!FULL_SCREEN_AVAILABLE) {
          return;
        }

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
          public void windowEnteringFullScreen(AppEvent.FullScreenEvent event) {
            JRootPane rootPane = frame.getRootPane();
            if (rootPane != null && rootPane.getBorder() != null && Registry.is("ide.mac.transparentTitleBarAppearance")) {
              rootPane.setBorder(null);
            }
          }

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
            JRootPane rootPane = frame.getRootPane();
            if (rootPane instanceof IdeRootPane && Registry.is("ide.mac.transparentTitleBarAppearance")) {
              IdeRootPane ideRootPane = (IdeRootPane)rootPane;
              UIUtil.setCustomTitleBar(frame, ideRootPane, runnable -> Disposer.register(ideRootPane, () -> runnable.run()));
            }
            exitFullscreen();
            ActiveWindowsWatcher.addActiveWindow(frame);
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

        Foundation.executeOnMainThread(true, true, () -> {
          invoke(window, "setToolbar:", toolbar);
          invoke(window, "setShowsToolbarButton:", 1);
        });
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
      final BuildNumber build = ApplicationInfoImpl.getShadowInstance().getBuild();
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
          TransactionGuard.submitTransaction(ApplicationManager.getApplication(), () ->
            ourProtocolHandler.openLink(event.getURI()));
        }
      });
    }
  }

  @Override
  public boolean isInFullScreen() {
    return myInFullScreen;
  }

  @NotNull
  @Override
  public Promise<Boolean> toggleFullScreen(boolean state) {
    if (!SystemInfo.isMacOSLion || myFrame == null) {
      return Promises.rejectedPromise();
    }
    if (myInFullScreen == state) {
      return Promises.resolvedPromise(state);
    }

    AsyncPromise<Boolean> promise = new AsyncPromise<>();
    myDispatcher.addListener(new FSAdapter() {
      @Override
      public void windowExitedFullScreen(AppEvent.FullScreenEvent event) {
        promise.setResult(false);
        myDispatcher.removeListener(this);
      }

      @Override
      public void windowEnteredFullScreen(AppEvent.FullScreenEvent event) {
        promise.setResult(true);
        myDispatcher.removeListener(this);
      }
    });

    // temporary solution for the old Runtime
    if (enterFullScreenMethod == null || leaveFullScreenMethod == null) {
      myFullscreenQueue.runOrEnqueue(this::toggleFullScreenNow);
    } else {
      myFullscreenQueue.runOrEnqueue(state ? this::enterFullScreenNow : this::leaveFullScreenNow);
    }

    return promise;
  }

  public void toggleFullScreenNow() {
    try {
      requestToggleFullScreenMethod.invoke(Application.getApplication(), myFrame);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  private void enterFullScreenNow() {
    try {
      enterFullScreenMethod.invoke(Application.getApplication(), myFrame);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  private void leaveFullScreenNow() {
    try {
      leaveFullScreenMethod.invoke(Application.getApplication(), myFrame);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }
}
