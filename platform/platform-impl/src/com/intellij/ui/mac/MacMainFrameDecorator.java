// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.apple.eawt.Application;
import com.apple.eawt.FullScreenAdapter;
import com.apple.eawt.FullScreenListener;
import com.apple.eawt.FullScreenUtilities;
import com.apple.eawt.event.FullScreenEvent;
import com.intellij.ide.ActiveWindowsWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarKt;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.ToolbarUtil;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
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

  private final EventDispatcher<FSListener> myDispatcher = EventDispatcher.create(FSListener.class);
  private final MacWinTabsHandler myTabsHandler;
  private boolean myInFullScreen;
  private boolean myIsInit;
  private boolean myCallSetFullScreenAfterInit;

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
          if (ExperimentalUI.isNewUI() && MainToolbarKt.isToolbarInHeader()) {
            ToolbarUtil.removeSystemTitleBar(rootPane);
          }
          else {
            ToolbarUtil.setCustomTitleBar(myFrame, rootPane, runnable -> {
              if (!Disposer.isDisposed(parentDisposable)) {
                Disposer.register(parentDisposable, runnable::run);
              }
            });
          }

          exitFullScreen();
          ActiveWindowsWatcher.addActiveWindow(myFrame);
          myFrame.validate();
        }
      });
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
    myIsInit = true;
    myTabsHandler.frameInit();
    if (myCallSetFullScreenAfterInit) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Sets full screen after init frame: " + myFrame);
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
      LOG.debug("Full screen state " + state + " requested for " + myFrame);
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
          LOG.debug("Full screen is already at state " + state + " for " + myFrame);
        }
        promise.complete(state);
      }
      else if (toggleFullScreenMethod == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Full screen transitioning isn't supported for " + myFrame);
        }
        promise.complete(null);
      }
      else {
        if (!myIsInit && !myFrame.isValid()) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Sets full screen before init frame: " + myFrame);
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
              LOG.debug("entering full screen: " + myFrame);
            }
            preEventReceived.set(true);
          }

          @Override
          public void windowExitingFullScreen(FullScreenEvent e) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("exiting full screen: " + myFrame);
            }
            preEventReceived.set(true);
          }

          @Override
          public void windowExitedFullScreen(FullScreenEvent event) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("exited full screen: " + myFrame);
            }
            promise.complete(false);
          }

          @Override
          public void windowEnteredFullScreen(FullScreenEvent event) {
            if (LOG.isDebugEnabled()) {
              LOG.debug("entered full screen: " + myFrame);
            }
            promise.complete(true);
          }
        };
        promise.whenComplete((aBoolean, throwable) -> myDispatcher.removeListener(listener));
        myDispatcher.addListener(listener);

        if (LOG.isDebugEnabled()) {
          LOG.debug("Toggling full screen for " + myFrame);
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
                LOG.debug("pre-transitioning event received for: " + myFrame);
              }
            }
            else {
              if (LOG.isDebugEnabled()) {
                LOG.debug("pre-transitioning event not received for: " + myFrame);
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
      method.invoke(Application.getApplication(), myFrame);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
  }

  @Override
  public void appClosing() {
    myTabsHandler.appClosing();
  }

  private interface FSListener extends FullScreenListener, EventListener {
  }

  private static class FSAdapter extends FullScreenAdapter implements FSListener {
  }
}
