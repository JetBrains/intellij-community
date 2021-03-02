// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.JBDimension;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;

/**
 * @author Alexander Lobas
 */
public class MacWinTabsHandler {
  private static final String WIN_TAB_FILLER = "WIN_TAB_FILLER_KEY";

  private final JFrame myFrame;
  private boolean myShowFrame;
  private boolean myInitFrame;

  @SuppressWarnings("FieldCanBeLocal")
  private static Callback myObserverCallback; // don't convert to local var
  private static ID myObserverDelegate;

  @NotNull
  public static JComponent wrapRootPaneNorthSide(@NotNull JRootPane rootPane, @NotNull JComponent northComponent) {
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

  public MacWinTabsHandler(@NotNull JFrame frame, @NotNull Disposable parentDisposable) {
    myFrame = frame;

    if (JdkEx.setTabbingMode(frame, () -> updateTabBars(null))) {
      Foundation.invoke("NSWindow", "setAllowsAutomaticWindowTabbing:", true);

      Disposer.register(parentDisposable, new Disposable() { // don't convert to lambda
        @Override
        public void dispose() {
          updateTabBars(null);
        }
      });
    }
  }

  public void frameShow() {
    myShowFrame = true;
    if (myInitFrame) {
      initUpdateTabBars();
    }
  }

  public void setProject() {
    myInitFrame = true;
    if (myShowFrame) {
      initUpdateTabBars();
    }
  }

  public void enteringFullScreen() {
    updateTabBar(myFrame, 0);
  }

  public void enterFullScreen() {
    updateTabBar(myFrame, 0);
  }

  public void exitFullScreen() {
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

  private void initUpdateTabBars() {
    // update tab logic only after call [NSWindow makeKeyAndOrderFront] and after add frame to window manager
    ApplicationManager.getApplication().invokeLater(() -> updateTabBars(myFrame));
  }

  private static void updateTabBars(@Nullable JFrame newFrame) {
    if (!JdkEx.isTabbingModeAvailable()) {
      return;
    }

    IdeFrame[] frames = WindowManager.getInstance().getAllProjectFrames();

    if (frames.length < 2) {
      if (frames.length == 1) {
        updateTabBar(frames[0], 0);

        if (newFrame == null) {
          ProjectFrameHelper helper = (ProjectFrameHelper)frames[0];
          if (helper.isInFullScreen()) {
            IdeFrameImpl frame = helper.getFrame();
            if (frame != null) {
              handleFullScreenResize(frame);
            }
          }
        }
        else {
          Foundation.executeOnMainThread(true, false, () -> {
            addTabObserver(MacUtil.getWindowFromJavaWindow(newFrame));
          });
        }
      }
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      Integer[] visibleAndHeights = new Integer[frames.length];
      boolean callInAppkit = false;
      int newIndex = -1;

      for (int i = 0; i < frames.length; i++) {
        ProjectFrameHelper helper = (ProjectFrameHelper)frames[i];
        if (Disposer.isDisposed(helper)) {
          visibleAndHeights[i] = 0;
          continue;
        }
        if (newFrame == helper.getFrame()) {
          newIndex = i;
        }
        if (helper.isInFullScreen()) {
          visibleAndHeights[i] = 0;
        }
        else {
          callInAppkit = true;
        }
      }

      if (callInAppkit) {
        // call only for shown window and only in Appkit
        Foundation.executeOnMainThread(true, false, () -> {
          if (newFrame != null) {
            addTabObserver(MacUtil.getWindowFromJavaWindow(newFrame));
          }

          for (int i = 0; i < frames.length; i++) {
            if (visibleAndHeights[i] == null) {
              ID window = MacUtil.getWindowFromJavaWindow(((ProjectFrameHelper)frames[i]).getFrame());
              int styleMask = Foundation.invoke(window, "styleMask").intValue();
              if ((styleMask & (1 << 14)) != 0) { // NSWindowStyleMaskFullScreen
                visibleAndHeights[i] = 0;
              }
              else {
                visibleAndHeights[i] = (int)Foundation.invoke_fpret(window, "getTabBarVisibleAndHeight");
                if (visibleAndHeights[i] == -1) {
                  visibleAndHeights[i] = DEFAULT_WIN_TAB_HEIGHT();
                }
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
        if (newFrame != null) {
          Foundation.executeOnMainThread(true, false, () -> {
            addTabObserver(MacUtil.getWindowFromJavaWindow(newFrame));
          });
        }

        if (newIndex != -1) {
          visibleAndHeights[newIndex] = 0;
        }

        for (int i = 0; i < frames.length; i++) {
          updateTabBar(frames[i], visibleAndHeights[i]);
        }
      }
    });
  }

  private static void updateTabBar(@NotNull Object frameObject, int height) {
    JFrame frame = null;
    if (frameObject instanceof JFrame) {
      frame = (JFrame)frameObject;
    }
    else if (frameObject instanceof ProjectFrameHelper) {
      if (Disposer.isDisposed((Disposable)frameObject)) {
        return;
      }
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

  private static void handleFullScreenResize(@NotNull Window window) {
    try {
      Object cPlatformWindow = MacUtil.getPlatformWindow(window);
      if (cPlatformWindow != null) {
        Class<?> windowClass = cPlatformWindow.getClass();

        Method deliverMoveResize = ReflectionUtil
          .getDeclaredMethod(windowClass, "deliverMoveResizeEvent", int.class, int.class, int.class, int.class, boolean.class);
        if (deliverMoveResize == null) {
          return;
        }

        DisplayMode displayMode = window.getGraphicsConfiguration().getDevice().getDisplayMode();

        Foundation.executeOnMainThread(true, false, () -> {
          try {
            deliverMoveResize.invoke(cPlatformWindow, 0, 0, displayMode.getWidth(), displayMode.getHeight(), true);
          }
          catch (Throwable e) {
            Logger.getInstance(MacWinTabsHandler.class).error(e);
          }
        });
      }
    }
    catch (Throwable e) {
      Logger.getInstance(MacWinTabsHandler.class).error(e);
    }
  }

  private static int DEFAULT_WIN_TAB_HEIGHT() {
    return Registry.intValue("ide.mac.bigsur.window.with.tabs.height", 28);
  }

  private static void addTabObserver(@NotNull ID window) {
    ID tabGroup = Foundation.invoke(window, "tabGroup");
    if (!ID.NIL.equals(Foundation.invoke(tabGroup, "observationInfo"))) {
      return;
    }

    if (myObserverDelegate == null) {
      myObserverCallback = new Callback() {
        @SuppressWarnings("unused")
        public void callback(ID self, Pointer selector, ID keyPath, ID ofObject, ID change, Pointer context) {
          ApplicationManager.getApplication().invokeLater(() -> updateTabBars(null));
        }
      };

      ID delegateClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), "MyWindowTabGroupObserver");
      Foundation.addMethod(delegateClass, Foundation.createSelector("observeValueForKeyPath:ofObject:change:context:"),
                           myObserverCallback, "v*");
      Foundation.registerObjcClassPair(delegateClass);

      myObserverDelegate = Foundation.invoke("MyWindowTabGroupObserver", "new");
    }

    Foundation.invoke(tabGroup, "addObserver:forKeyPath:options:context:", myObserverDelegate, Foundation.nsString("windows"), 0, ID.NIL);
  }

  public static void switchFrameIfPossible(@NotNull JFrame frame, boolean next) {
    if (JdkEx.isTabbingModeAvailable()) {
      Foundation.executeOnMainThread(true, false, () -> {
        Foundation.invoke(MacUtil.getWindowFromJavaWindow(frame), next ? "selectNextTab:" : "selectPreviousTab:", ID.NIL);
      });
    }
  }
}