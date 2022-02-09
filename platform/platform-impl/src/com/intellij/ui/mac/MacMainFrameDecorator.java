// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.apple.eawt.Application;
import com.apple.eawt.FullScreenAdapter;
import com.apple.eawt.FullScreenListener;
import com.apple.eawt.FullScreenUtilities;
import com.apple.eawt.event.FullScreenEvent;
import com.intellij.ide.ActiveWindowsWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.ui.ToolbarUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Native;
import com.sun.jna.platform.mac.CoreFoundation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.EventListener;

public final class MacMainFrameDecorator extends IdeFrameDecorator {
  public static final String FULL_SCREEN = "Idea.Is.In.FullScreen.Mode.Now";
  private static Method toggleFullScreenMethod;

  static {
    try {
      //noinspection SpellCheckingInspection
      Class.forName("com.apple.eawt.FullScreenUtilities");
      toggleFullScreenMethod = Application.class.getMethod("requestToggleFullScreen", Window.class);
    }
    catch (Exception e) {
      Logger.getInstance(MacMainFrameDecorator.class).warn(e);
    }
  }
  interface MyCoreFoundation extends CoreFoundation {
    MyCoreFoundation INSTANCE = Native.load("CoreFoundation", MyCoreFoundation.class);

    CoreFoundation.CFStringRef CFPreferencesCopyAppValue(
      CoreFoundation.CFStringRef key, CoreFoundation.CFStringRef applicationID);
  }

  private final EventDispatcher<FSListener> myDispatcher = EventDispatcher.create(FSListener.class);
  private final MacWinTabsHandler myTabsHandler;
  private boolean myInFullScreen;
  public MacMainFrameDecorator(@NotNull JFrame frame, @NotNull Disposable parentDisposable) {
    super(frame);

    myTabsHandler = new MacWinTabsHandler(frame, parentDisposable);

    if (toggleFullScreenMethod != null) {
      FullScreenUtilities.setWindowCanFullScreen(frame, true);

      // Native full screen listener can be set only once
      FullScreenUtilities.addFullScreenListenerTo(frame, new FullScreenListener() {
        @Override
        public void windowEnteringFullScreen(FullScreenEvent event) {
          myDispatcher.getMulticaster().windowEnteringFullScreen(event);
        }

        @Override
        public void windowEnteredFullScreen(FullScreenEvent event) {
          myDispatcher.getMulticaster().windowEnteredFullScreen(event);
        }

        @Override
        public void windowExitingFullScreen(FullScreenEvent event) {
          myDispatcher.getMulticaster().windowExitingFullScreen(event);
        }

        @Override
        public void windowExitedFullScreen(FullScreenEvent event) {
          myDispatcher.getMulticaster().windowExitedFullScreen(event);
        }
      });

      myDispatcher.addListener(new FSAdapter() {
        @Override
        public void windowEnteringFullScreen(FullScreenEvent event) {
          JRootPane rootPane = myFrame.getRootPane();
          if (rootPane != null && rootPane.getBorder() != null && Registry.is("ide.mac.transparentTitleBarAppearance")) {
            rootPane.setBorder(null);
          }
          myTabsHandler.enteringFullScreen();
        }

        @Override
        public void windowEnteredFullScreen(FullScreenEvent event) {
          // We can get the notification when the frame has been disposed
          JRootPane rootPane = myFrame.getRootPane();
          if (rootPane != null) rootPane.putClientProperty(FULL_SCREEN, Boolean.TRUE);
          enterFullScreen();
          myFrame.validate();
        }

        @Override
        public void windowExitedFullScreen(FullScreenEvent event) {
          // We can get the notification when the frame has been disposed
          JRootPane rootPane = myFrame.getRootPane();
          ToolbarUtil.setCustomTitleBar(myFrame, rootPane, runnable -> {
            if(!Disposer.isDisposed(parentDisposable)) {
              Disposer.register(parentDisposable, () -> runnable.run());
            }
          });

          exitFullScreen();
          ActiveWindowsWatcher.addActiveWindow(myFrame);
          myFrame.validate();
        }
      });
    }
    JRootPane rootPane = myFrame.getRootPane();

    if (rootPane != null && Registry.is("ide.mac.transparentTitleBarAppearance")) {

      IdeGlassPane glassPane = (IdeGlassPane)myFrame.getRootPane().getGlassPane();
      glassPane.addMousePreprocessor(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2 && e.getY() <= UIUtil.getTransparentTitleBarHeight(rootPane)) {
            CoreFoundation.CFStringRef appleActionOnDoubleClick = CoreFoundation.CFStringRef.createCFString("AppleActionOnDoubleClick");
            CoreFoundation.CFStringRef apple_global_domain = CoreFoundation.CFStringRef.createCFString("Apple Global Domain");
            CoreFoundation.CFStringRef res = MyCoreFoundation.INSTANCE.CFPreferencesCopyAppValue(
              appleActionOnDoubleClick,
              apple_global_domain);
            if (res != null && !res.stringValue().equals("Maximize")) {
              if (frame.getExtendedState() == Frame.ICONIFIED) {
                frame.setExtendedState(Frame.NORMAL);
              }
              else {
                frame.setExtendedState(Frame.ICONIFIED);
              }
            }
            else {
              if (frame.getExtendedState() == Frame.MAXIMIZED_BOTH) {
                frame.setExtendedState(Frame.NORMAL);
              }
              else {
                frame.setExtendedState(Frame.MAXIMIZED_BOTH);
              }
            }
            apple_global_domain.release();
            appleActionOnDoubleClick.release();
            if(res != null) {
              res.release();
            }
          }
          super.mouseClicked(e);
        }
      }, parentDisposable);
    }
  }

  private void enterFullScreen() {
    myInFullScreen = true;
    storeFullScreenStateIfNeeded();
    myTabsHandler.enterFullScreen();
  }

  private void exitFullScreen() {
    myInFullScreen = false;
    storeFullScreenStateIfNeeded();

    JRootPane rootPane = myFrame.getRootPane();
    if (rootPane != null) {
      rootPane.putClientProperty(FULL_SCREEN, null);
    }

    myTabsHandler.exitFullScreen();
  }

  private void storeFullScreenStateIfNeeded() {
    // todo should we really check that frame has not null project as it was implemented previously?
    myFrame.doLayout();
  }

  @Override
  public void frameInit() {
    myTabsHandler.frameInit();
  }

  @Override
  public void frameShow() {
    myTabsHandler.frameShow();
  }

  @Override
  public void setProject() {
    myTabsHandler.setProject();
  }

  @Override
  public boolean isInFullScreen() {
    return myInFullScreen;
  }

  @Override
  public @NotNull Promise<Boolean> toggleFullScreen(boolean state) {
    AsyncPromise<Boolean> promise = new AsyncPromise<>();
    // We delay the execution using 'invokeLater' to account for the case when window might be made visible in the same EDT event.
    // macOS can auto-open that window in full-screen mode, but we won't find this out till the notification arrives.
    // That notification comes as a priority event, so such an 'invokeLater' is enough to fix the problem.
    // Note, that subsequent invocations of current method in the same or close enough EDT events isn't supported well, but
    // such usage scenarios are not known at the moment.
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myInFullScreen == state) {
        promise.setResult(state);
      }
      else if (toggleFullScreenMethod == null) {
        promise.setResult(null);
      }
      else {
        myDispatcher.addListener(new FSAdapter() {
          @Override
          public void windowExitedFullScreen(FullScreenEvent event) {
            promise.setResult(false);
            myDispatcher.removeListener(this);
          }

          @Override
          public void windowEnteredFullScreen(FullScreenEvent event) {
            promise.setResult(true);
            myDispatcher.removeListener(this);
          }
        });

        invokeAppMethod(toggleFullScreenMethod);
      }
    });
    return promise;
  }

  private void invokeAppMethod(Method method) {
    try {
      method.invoke(Application.getApplication(), myFrame);
    }
    catch (Exception e) {
      Logger.getInstance(MacMainFrameDecorator.class).warn(e);
    }
  }

  private interface FSListener extends FullScreenListener, EventListener {
  }

  private static class FSAdapter extends FullScreenAdapter implements FSListener {
  }
}
