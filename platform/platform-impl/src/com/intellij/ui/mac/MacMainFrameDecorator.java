// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.apple.eawt.*;
import com.intellij.ide.ActiveWindowsWatcher;
import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameDecorator;
import com.intellij.openapi.wm.impl.IdeRootPane;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.EventDispatcher;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;
import java.util.EventListener;
import java.util.LinkedList;
import java.util.Queue;

public final class MacMainFrameDecorator extends IdeFrameDecorator {
  private interface FSListener extends FullScreenListener, EventListener {}
  private static class FSAdapter extends FullScreenAdapter implements FSListener {}

  private static class FullScreenQueue {
    private final Queue<Runnable> myQueue = new LinkedList<>();
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

    updateTabBar(myFrame, 0);
  }

  private void exitFullScreen() {
    myInFullScreen = false;
    storeFullScreenStateIfNeeded();

    JRootPane rootPane = myFrame.getRootPane();
    if (rootPane != null) rootPane.putClientProperty(FULL_SCREEN, null);
    myFullScreenQueue.runFromQueue();

    updateTabBarOnExitFromFullScreen();
  }

  private void storeFullScreenStateIfNeeded() {
    // todo should we really check that frame has not null project as it was implemented previously?
    myFrame.doLayout();
  }

  public static final String FULL_SCREEN = "Idea.Is.In.FullScreen.Mode.Now";

  private static Method toggleFullScreenMethod;
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
        toggleFullScreenMethod = Application.class.getMethod("requestToggleFullScreen", Window.class);
      }
    }
    catch (Exception e) {
      Logger.getInstance(MacMainFrameDecorator.class).debug(e);
    }
  }

  private final FullScreenQueue myFullScreenQueue = new FullScreenQueue();
  private final EventDispatcher<FSListener> myDispatcher = EventDispatcher.create(FSListener.class);
  private boolean myInFullScreen;

  private static final String WIN_TAB_FILLER = "WIN_TAB_FILLER_KEY";

  private static int DEFAULT_WIN_TAB_HEIGHT() {
    return Registry.intValue("ide.mac.bigsur.window.with.tabs.height", 28);
  }

  @NotNull
  public static JComponent _wrapRootPaneNorthSide(@NotNull JRootPane rootPane, @NotNull JComponent northComponent) {
    if (!JdkEx.isTabbingModeAvailable()) {
      return northComponent;
    }

    NonOpaquePanel panel = new NonOpaquePanel(new BorderLayout());

    NonOpaquePanel filler = new NonOpaquePanel();
    filler.setVisible(false);

    panel.add(filler, BorderLayout.NORTH);
    panel.add(northComponent);
    rootPane.putClientProperty(WIN_TAB_FILLER, filler);
    return panel;
  }

  public MacMainFrameDecorator(@NotNull JFrame frame, @NotNull Disposable parentDisposable) {
    super(frame);

    if (JdkEx.setTabbingMode(frame, () -> updateTabBars(null))) {
      Disposer.register(parentDisposable, new Disposable() { // don't convert to lambda
        @Override
        public void dispose() {
          updateTabBars(null);
        }
      });
    }

    if (leaveFullScreenMethod != null || toggleFullScreenMethod != null) {
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
          updateTabBar(myFrame, 0);
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
  public void frameShow() {
    // update tab logic only after call [NSWindow makeKeyAndOrderFront]
    updateTabBars(myFrame);
  }

  private static void updateTabBars(@Nullable JFrame newFrame) {
    if (!JdkEx.isTabbingModeAvailable()) {
      return;
    }

    IdeFrame[] frames = WindowManager.getInstance().getAllProjectFrames();

    if (frames.length < 2) {
      if (frames.length == 1) {
        updateTabBar(frames[0], 0);
      }
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      Integer[] visibleAndHeights = new Integer[frames.length];
      boolean callInAppkit = false;
      int newIndex = -1;

      for (int i = 0; i < frames.length; i++) {
        ProjectFrameHelper helper = (ProjectFrameHelper)frames[i];
        if (newFrame == helper.getFrame()) {
          newIndex = i;
        }
        else if (helper.isInFullScreen()) {
          visibleAndHeights[i] = 0;
        }
        else {
          callInAppkit = true;
        }
      }

      if (callInAppkit) {
        // call only for shown window and only in Appkit
        Foundation.executeOnMainThread(true, false, () -> {
          for (int i = 0; i < frames.length; i++) {
            if (visibleAndHeights[i] == null) {
              ID window = MacUtil.getWindowFromJavaWindow(((ProjectFrameHelper)frames[i]).getFrame());
              visibleAndHeights[i] = (int)Foundation.invoke_fpret(window, "getTabBarVisibleAndHeight");
              if (visibleAndHeights[i] == -1) {
                visibleAndHeights[i] = DEFAULT_WIN_TAB_HEIGHT();
              }
            }
          }

          ApplicationManager.getApplication().invokeLater(() -> {
            for (int i = 0; i < frames.length; i++) {
              updateTabBar(frames[i], visibleAndHeights[i]);
            }
          });
        });
      }
      else {
        if (newIndex != -1) {
          visibleAndHeights[newIndex] = 0;
        }

        for (int i = 0; i < frames.length; i++) {
          updateTabBar(frames[i], visibleAndHeights[i]);
        }
      }
    });
  }

  private void updateTabBarOnExitFromFullScreen() {
    if (!JdkEx.isTabbingModeAvailable()) {
      return;
    }

    Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(myFrame);
      int visibleAndHeight = (int)Foundation.invoke_fpret(window, "getTabBarVisibleAndHeight");
      ApplicationManager.getApplication()
        .invokeLater(() -> updateTabBar(myFrame, visibleAndHeight == -1 ? DEFAULT_WIN_TAB_HEIGHT() : visibleAndHeight));
    });
  }

  private static void updateTabBar(@NotNull Object frameObject, int height) {
    JFrame frame = null;
    if (frameObject instanceof JFrame) {
      frame = (JFrame)frameObject;
    }
    else if (frameObject instanceof ProjectFrameHelper) {
      frame = ((ProjectFrameHelper)frameObject).getFrame();
    }
    if (frame == null) {
      return;
    }

    JComponent filler = (JComponent)frame.getRootPane().getClientProperty(WIN_TAB_FILLER);
    if (filler == null) {
      return;
    }
    boolean visible = height > 0;
    boolean oldVisible = filler.isVisible();
    filler.setVisible(visible);
    filler.setPreferredSize(new JBDimension(-1, height));

    Container parent = filler.getParent();
    if (parent == null || oldVisible == visible) {
      return;
    }
    parent.doLayout();
    parent.revalidate();
    parent.repaint();
  }

  @Override
  public boolean isInFullScreen() {
    return myInFullScreen;
  }

  @Override
  public @NotNull Promise<Boolean> toggleFullScreen(boolean state) {
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
    if (toggleFullScreenMethod != null) {
      myFullScreenQueue.runOrEnqueue(() -> invokeAppMethod(toggleFullScreenMethod));
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
