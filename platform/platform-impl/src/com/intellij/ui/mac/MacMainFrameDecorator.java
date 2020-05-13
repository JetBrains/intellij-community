// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.apple.eawt.*;
import com.intellij.ide.ActiveWindowsWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.EventListener;
import java.util.LinkedList;

public final class MacMainFrameDecorator extends IdeFrameDecorator {
  private interface FSListener extends FullScreenListener, EventListener {}
  private static class FSAdapter extends FullScreenAdapter implements FSListener {}

  private static class FullScreenQueue {
    private final LinkedList<Runnable> myQueue = new LinkedList<>();
    private boolean myWaitingForAppKit = false;

    synchronized void runOrEnqueue(Runnable runnable) {
      if (myWaitingForAppKit) {
        myQueue.add(runnable);
      }
      else {
        ApplicationManager.getApplication().invokeLater(runnable);
        myWaitingForAppKit = true;
      }
    }

    synchronized void runFromQueue() {
      if (!myQueue.isEmpty()) {
        myQueue.remove().run();
        myWaitingForAppKit = true;
      }
      else {
        myWaitingForAppKit = false;
      }
    }
  }

  private void enterFullScreen() {
    myInFullScreen = true;
    storeFullScreenStateIfNeeded();
    myFullScreenQueue.runFromQueue();
  }

  private void exitFullScreen() {
    myInFullScreen = false;
    storeFullScreenStateIfNeeded();

    JRootPane rootPane = myFrame.getRootPane();
    if (rootPane != null) rootPane.putClientProperty(FULL_SCREEN, null);
    myFullScreenQueue.runFromQueue();
  }

  private void storeFullScreenStateIfNeeded() {
    // todo should we really check that frame has not null project as it was implemented previously?
    myFrame.doLayout();
  }

  public static final String FULL_SCREEN = "Idea.Is.In.FullScreen.Mode.Now";

  private static Method requestToggleFullScreenMethod;
  private static Method enterFullScreenMethod;
  private static Method leaveFullScreenMethod;

  static {
    try {
      //noinspection SpellCheckingInspection
      Class.forName("com.apple.eawt.FullScreenUtilities");
      try {
        //noinspection JavaReflectionMemberAccess
        enterFullScreenMethod = Application.class.getMethod("requestEnterFullScreen", Window.class);
        //noinspection JavaReflectionMemberAccess
        leaveFullScreenMethod = Application.class.getMethod("requestLeaveFullScreen", Window.class);
      }
      catch (NoSuchMethodException e) {
        // temporary solution for the old runtime
        //noinspection JavaReflectionMemberAccess
        requestToggleFullScreenMethod = Application.class.getMethod("requestToggleFullScreen", Window.class);
      }
    }
    catch (Exception e) {
      Logger.getInstance(MacMainFrameDecorator.class).debug(e);
    }
  }

  private final FullScreenQueue myFullScreenQueue = new FullScreenQueue();
  private final EventDispatcher<FSListener> myDispatcher = EventDispatcher.create(FSListener.class);
  private boolean myInFullScreen;

  public MacMainFrameDecorator(@NotNull JFrame frame, @NotNull Disposable parentDisposable) {
    super(frame);

    if (leaveFullScreenMethod != null || requestToggleFullScreenMethod != null) {
      FullScreenUtilities.setWindowCanFullScreen(frame, true);

      // Native full screen listener can be set only once
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
          JRootPane rootPane = myFrame.getRootPane();
          if (rootPane != null && rootPane.getBorder() != null && Registry.is("ide.mac.transparentTitleBarAppearance")) {
            rootPane.setBorder(null);
          }
        }

        @Override
        public void windowEnteredFullScreen(AppEvent.FullScreenEvent event) {
          // We can get the notification when the frame has been disposed
          JRootPane rootPane = myFrame.getRootPane();
          if (rootPane != null) rootPane.putClientProperty(FULL_SCREEN, Boolean.TRUE);
          enterFullScreen();
          myFrame.validate();
        }

        @Override
        public void windowExitedFullScreen(AppEvent.FullScreenEvent event) {
          // We can get the notification when the frame has been disposed
          JRootPane rootPane = myFrame.getRootPane();
          if (rootPane instanceof IdeRootPane && Registry.is("ide.mac.transparentTitleBarAppearance")) {
            IdeRootPane ideRootPane = (IdeRootPane)rootPane;
            UIUtil.setCustomTitleBar(myFrame, ideRootPane, runnable -> {
              Disposer.register(parentDisposable, () -> runnable.run());
            });
          }
          exitFullScreen();
          ActiveWindowsWatcher.addActiveWindow(myFrame);
          myFrame.validate();
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

    // temporary solution for the old runtime
    if (requestToggleFullScreenMethod != null) {
      myFullScreenQueue.runOrEnqueue(() -> invokeAppMethod(requestToggleFullScreenMethod));
    }
    else if (state) {
      myFullScreenQueue.runOrEnqueue(() -> invokeAppMethod(enterFullScreenMethod));
    }
    else {
      myFullScreenQueue.runOrEnqueue(() -> invokeAppMethod(leaveFullScreenMethod));
    }

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
}