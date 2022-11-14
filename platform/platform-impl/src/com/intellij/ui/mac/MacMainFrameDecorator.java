// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.apple.eawt.Application;
import com.apple.eawt.FullScreenAdapter;
import com.apple.eawt.FullScreenListener;
import com.apple.eawt.FullScreenUtilities;
import com.apple.eawt.event.FullScreenEvent;
import com.intellij.ide.ActiveWindowsWatcher;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarKt;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.ToolbarUtil;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.CustomWindowDecoration;
import com.jetbrains.JBR;
import com.sun.jna.Native;
import com.sun.jna.platform.mac.CoreFoundation;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.EventListener;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MacMainFrameDecorator extends IdeFrameDecorator {
  private static final Logger LOG = Logger.getInstance(MacMainFrameDecorator.class);

  public static final String FULL_SCREEN = "Idea.Is.In.FullScreen.Mode.Now";
  private static Method toggleFullScreenMethod;

  static {
    try {
      //noinspection SpellCheckingInspection
      Class.forName("com.apple.eawt.FullScreenUtilities");
      toggleFullScreenMethod = Application.class.getMethod("requestToggleFullScreen", Window.class);
    }
    catch (Exception e) {
      LOG.warn(e);
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
  private boolean myIsInit;
  private boolean myCallSetFullScreenAfterInit;

  public MacMainFrameDecorator(@NotNull JFrame frame, @NotNull IdeGlassPane glassPane, @NotNull Disposable parentDisposable) {
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
          JRootPane rootPane = frame.getRootPane();
          if (rootPane != null && rootPane.getBorder() != null) {
            rootPane.setBorder(null);
          }
          myTabsHandler.enteringFullScreen();
        }

        @Override
        public void windowEnteredFullScreen(FullScreenEvent event) {
          // We can get the notification when the frame has been disposed
          JRootPane rootPane = frame.getRootPane();
          if (rootPane != null) {
            rootPane.putClientProperty(FULL_SCREEN, Boolean.TRUE);
          }
          enterFullScreen();
          frame.validate();
        }

        @Override
        public void windowExitedFullScreen(FullScreenEvent event) {
          // We can get the notification when the frame has been disposed
          JRootPane rootPane = frame.getRootPane();
          if (ExperimentalUI.isNewUI() && MainToolbarKt.isToolbarInHeader(UISettings.getShadowInstance())) {
            ToolbarUtil.removeMacSystemTitleBar(rootPane);
          }
          else {
            ToolbarUtil.setCustomTitleBar(frame, rootPane, runnable -> {
              if (!Disposer.isDisposed(parentDisposable)) {
                Disposer.register(parentDisposable, runnable::run);
              }
            });
          }

          exitFullScreen();
          ActiveWindowsWatcher.addActiveWindow(frame);
          frame.validate();
        }
      });
    }

    glassPane.addMousePreprocessor(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && e.getY() <= UIUtil.getTransparentTitleBarHeight(frame.getRootPane())) {
          int hitTestSpot = CustomWindowDecoration.NO_HIT_SPOT;
          if (JBR.isCustomWindowDecorationSupported()) {
            var spots = JBR.getCustomWindowDecoration().getCustomDecorationHitTestSpots(frame);
            if (spots != null) {
              for (var spot : spots) {
                if (spot.getKey().contains(e.getPoint())) {
                  hitTestSpot = spot.getValue();
                  break;
                }
              }
            }
          }
          if (hitTestSpot != CustomWindowDecoration.NO_HIT_SPOT) {
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
            if (res != null) {
              res.release();
            }
          }
        }
        super.mouseClicked(e);
      }
    }, parentDisposable);
  }

  private void enterFullScreen() {
    myInFullScreen = true;
    storeFullScreenStateIfNeeded();
    myTabsHandler.enterFullScreen();
  }

  private void exitFullScreen() {
    myInFullScreen = false;
    storeFullScreenStateIfNeeded();

    JRootPane rootPane = frame.getRootPane();
    if (rootPane != null) {
      rootPane.putClientProperty(FULL_SCREEN, null);
    }

    myTabsHandler.exitFullScreen();
  }

  private void storeFullScreenStateIfNeeded() {
    // todo should we really check that frame has not null project as it was implemented previously?
    frame.doLayout();
  }

  @Override
  public void frameInit() {
    myIsInit = true;
    myTabsHandler.frameInit();
    if (myCallSetFullScreenAfterInit) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Sets full screen after init frame: " + frame);
      }
      myCallSetFullScreenAfterInit = false;
      toggleFullScreen(true);
    }
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
  public @NotNull CompletableFuture<Boolean> toggleFullScreen(boolean state) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Full screen state " + state + " requested for " + frame);
    }
    CompletableFuture<Boolean> promise = new CompletableFuture<>();
    // We delay the execution using 'invokeLater' to account for the case when window might be made visible in the same EDT event.
    // macOS can auto-open that window in full-screen mode, but we won't find this out till the notification arrives.
    // That notification comes as a priority event, so such an 'invokeLater' is enough to fix the problem.
    // Note, that subsequent invocations of current method in the same or close enough EDT events isn't supported well, but
    // such usage scenarios are not known at the moment.
    SwingUtilities.invokeLater(() -> {
      if (myInFullScreen == state) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Full screen is already at state " + state + " for " + frame);
        }
        promise.complete(state);
      }
      else if (toggleFullScreenMethod == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Full screen transitioning isn't supported for " + frame);
        }
        promise.complete(null);
      }
      else {
        if (!myIsInit && !frame.isValid()) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Sets full screen before init frame: " + frame);
          }
          myCallSetFullScreenAfterInit = true;
          promise.complete(false);
          return;
        }
        AtomicBoolean preEventReceived = new AtomicBoolean();
        FSAdapter listener = new FSAdapter() {
          @Override
          public void windowEnteringFullScreen(FullScreenEvent e) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("entering full screen: " + frame);
            }
            preEventReceived.set(true);
          }

          @Override
          public void windowExitingFullScreen(FullScreenEvent e) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("exiting full screen: " + frame);
            }
            preEventReceived.set(true);
          }

          @Override
          public void windowExitedFullScreen(FullScreenEvent event) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("exited full screen: " + frame);
            }
            promise.complete(false);
          }

          @Override
          public void windowEnteredFullScreen(FullScreenEvent event) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("entered full screen: " + frame);
            }
            promise.complete(true);
          }
        };
        promise.whenComplete((aBoolean, throwable) -> myDispatcher.removeListener(listener));
        myDispatcher.addListener(listener);

        if (LOG.isDebugEnabled()) {
          LOG.debug("Toggling full screen for " + frame);
        }
        invokeAppMethod(toggleFullScreenMethod);

        Foundation.executeOnMainThread(false, false, () -> {
          SwingUtilities.invokeLater(() -> {
            // At this point, after a 'round-trip' to AppKit thread and back to EDT,
            // we know that [NSWindow toggleFullScreen:] method has definitely started execution.
            // If it hasn't dispatched pre-transitioning event (windowWillEnterFullScreen/windowWillExitFullScreen), we assume that
            // the transitioning won't happen at all, and complete the promise. One known case when [NSWindow toggleFullScreen:] method
            // does nothing is when it's invoked for an 'inactive' tab in a 'tabbed' window group.
            if (preEventReceived.get()) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("pre-transitioning event received for: " + frame);
              }
            }
            else {
              if (LOG.isDebugEnabled()) {
                LOG.debug("pre-transitioning event not received for: " + frame);
              }
              promise.complete(myInFullScreen);
            }
          });
        });
      }
    });
    return promise;
  }

  private void invokeAppMethod(Method method) {
    try {
      method.invoke(Application.getApplication(), frame);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  @Override
  public void appClosing() {
    myTabsHandler.appClosing();
  }

  @Override
  public boolean isTabbedWindow() {
    return MergeAllWindowsAction.isTabbedWindow(frame);
  }

  private interface FSListener extends FullScreenListener, EventListener {
  }

  private static class FSAdapter extends FullScreenAdapter implements FSListener {
  }
}
