// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.components.panels.OpaquePanel;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;

import static com.intellij.openapi.wm.impl.IdeGlassPaneImplKt.executeOnCancelInEdt;

/**
 * @author Alexander Lobas
 */
public class MacWinTabsHandler {
  private static final String WIN_TAB_FILLER = "WIN_TAB_FILLER_KEY";
  private static final String CLOSE_MARKER = "TABS_CLOSE_MARKER";

  protected final IdeFrameImpl myFrame;
  private final boolean myFrameAllowed;

  @SuppressWarnings("FieldCanBeLocal")
  private static Callback myObserverCallback; // don't convert to local var
  private static ID myObserverDelegate;

  public static @NotNull JComponent createAndInstallHandlerComponent(@NotNull JRootPane rootPane) {
    if (isVersion2()) {
      return MacWinTabsHandlerV2._createAndInstallHandlerComponent(rootPane);
    }

    JPanel filler = new OpaquePanel();
    filler.setBorder(JBUI.Borders.customLineBottom(UIUtil.getTooltipSeparatorColor()));
    filler.setVisible(false);

    rootPane.putClientProperty(WIN_TAB_FILLER, filler);
    rootPane.putClientProperty("Window.transparentTitleBarHeight", 28);
    return filler;
  }

  public static void fastInit(@NotNull IdeFrameImpl frame) {
    if (isVersion2()) {
      MacWinTabsHandlerV2._fastInit(frame);
      return;
    }
    if (JdkEx.setTabbingMode(frame, getWindowId(), () -> updateTabBars(null))) {
      Foundation.invoke("NSWindow", "setAllowsAutomaticWindowTabbing:", true);
    }
  }

  public static boolean isVersion2() {
    return ExperimentalUI.isNewUI() && Registry.is("ide.mac.os.wintabs.version2", true);
  }

  public MacWinTabsHandler(@NotNull IdeFrameImpl frame, @NotNull CoroutineScope coroutineScope) {
    myFrame = frame;
    myFrameAllowed = initFrame(frame, coroutineScope);
  }

  protected boolean initFrame(@NotNull IdeFrameImpl frame, @NotNull CoroutineScope coroutineScope) {
    boolean allowed = isAllowedFrame(frame) && JdkEx.setTabbingMode(frame, getWindowId(), () -> updateTabBars(null));

    if (allowed) {
      Foundation.invoke("NSWindow", "setAllowsAutomaticWindowTabbing:", true);

      executeOnCancelInEdt(coroutineScope, () -> {
        updateTabBars(null);
        return Unit.INSTANCE;
      });
    }

    return allowed;
  }

  private static boolean isAllowedFrame(@Nullable JFrame frame) {
    return frame == null || frame instanceof IdeFrameImpl;
  }

  static @NotNull String getWindowId() {
    return (isVersion2() ? "+" : "") +
           ApplicationNamesInfo.getInstance().getProductName() +
           (PluginManagerCore.isRunningFromSources() ? "-Snapshot" : "") +
           "-AwtWindow-WithTabs";
  }

  public void setProject() {
    // update tab logic only after call [NSWindow makeKeyAndOrderFront] and after add frame to window manager
    ApplicationManager.getApplication().invokeLater(() -> updateTabBars(myFrame));
  }

  public void enteringFullScreen() {
    enterFullScreen();
  }

  public void enterFullScreen() {
    if (!myFrameAllowed) {
      return;
    }
    updateTabBar();
  }

  public void exitFullScreen() {
    if (myFrameAllowed) {
      updateTabBar();
    }
  }

  private void updateTabBar() {
    Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(myFrame);
      int visibleAndHeight = (int)Foundation.invoke_fpret(window, "getTabBarVisibleAndHeight");
      ApplicationManager.getApplication()
        .invokeLater(() -> updateTabBar(myFrame, visibleAndHeight == -1 ? DEFAULT_WIN_TAB_HEIGHT() : visibleAndHeight));
    });
  }

  static void updateTabBars(@Nullable JFrame newFrame) {
    if (!isAllowedFrame(newFrame) || !JdkEx.isTabbingModeAvailable()) {
      return;
    }

    IdeFrame[] frames = WindowManager.getInstance().getAllProjectFrames();

    if (frames.length < 2) {
      if (frames.length == 1) {
        updateTabBar(frames[0], 0);

        if (newFrame == null) {
          ProjectFrameHelper helper = (ProjectFrameHelper)frames[0];
          if (helper.isInFullScreen()) {
            handleFullScreenResize(helper.getFrame());
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
        if (helper.isDisposed$intellij_platform_ide_impl()) {
          visibleAndHeights[i] = 0;
          continue;
        }
        if (newFrame == helper.getFrame()) {
          newIndex = i;
        }
        else {
          callInAppkit = true;
        }
      }

      if (callInAppkit) {
        // call only for shown window and only in Appkit
        Foundation.executeOnMainThread(true, false, () -> {
          for (int i = 0; i < frames.length; i++) {
            ID window = MacUtil.getWindowFromJavaWindow(((ProjectFrameHelper)frames[i]).getFrame());
            addTabObserver(window);

            if (visibleAndHeights[i] == null) {
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
        Foundation.executeOnMainThread(true, false, () -> {
          for (IdeFrame frame : frames) {
            addTabObserver(MacUtil.getWindowFromJavaWindow(((ProjectFrameHelper)frame).getFrame()));
          }
        });

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
    JRootPane rootPane;
    if (frameObject instanceof JFrame) {
      rootPane = ((JFrame)frameObject).getRootPane();
    }
    else if (frameObject instanceof ProjectFrameHelper frameHelper) {
      rootPane = frameHelper.getFrame().getRootPane();
    }
    else {
      return;
    }

    if (rootPane == null) {
      return;
    }

    JComponent filler = (JComponent)rootPane.getClientProperty(WIN_TAB_FILLER);
    if (filler == null) {
      return;
    }

    if (height > 0) {
      height++;
    }

    boolean visible = height > 0;
    boolean oldVisible = filler.isVisible();
    filler.setVisible(visible);
    filler.setPreferredSize(new Dimension(-1, height)); // native header doesn't scale

    Container parent = filler.getParent();
    if (parent == null || oldVisible == visible) {
      return;
    }

    parent.doLayout();
    parent.revalidate();
    parent.repaint();
  }

  protected static void handleFullScreenResize(@NotNull Window window) {
    try {
      Object cPlatformWindow = MacUtil.getPlatformWindow(window);
      if (cPlatformWindow != null) {
        Class<?> windowClass = cPlatformWindow.getClass();

        Method deliverMoveResize = ReflectionUtil.getDeclaredMethod(windowClass, "doDeliverMoveResizeEvent");
        if (deliverMoveResize != null) {
          try {
            deliverMoveResize.invoke(cPlatformWindow);
          }
          catch (Throwable e) {
            Logger.getInstance(MacWinTabsHandler.class).error(e);
          }
          return;
        }

        Method javaDeliverMoveResize = ReflectionUtil
          .getDeclaredMethod(windowClass, "deliverMoveResizeEvent", int.class, int.class, int.class, int.class, boolean.class);
        if (javaDeliverMoveResize == null) {
          return;
        }

        Rectangle rect = window.getGraphicsConfiguration().getBounds();

        Foundation.executeOnMainThread(true, false, () -> {
          try {
            javaDeliverMoveResize.invoke(cPlatformWindow, rect.x, rect.y, rect.width, rect.height, true);
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

  public void appClosing() {
    if (!myFrameAllowed) {
      return;
    }

    IdeFrame[] frames = WindowManager.getInstance().getAllProjectFrames();
    int length = frames.length;

    if (length < 2) {
      return;
    }

    ID[] windows = new ID[length];

    for (int i = 0; i < length; i++) {
      IdeFrameImpl frame = ((ProjectFrameHelper)frames[i]).getFrame();
      JRootPane pane = frame.getRootPane();
      if (pane.getClientProperty(CLOSE_MARKER) != null) {
        return;
      }
      pane.putClientProperty(CLOSE_MARKER, Boolean.TRUE);
      windows[i] = MacUtil.getWindowFromJavaWindow(frame);
    }

    ID tabs = Foundation.invoke(windows[0], "tabbedWindows");

    if (Foundation.invoke(tabs, "count").intValue() != length) {
      return;
    }

    IdeFrame[] orderedFrames = new IdeFrame[length];

    for (int i = 0; i < length; i++) {
      int index = Foundation.invoke(tabs, "indexOfObject:", windows[i]).intValue();
      orderedFrames[index] = frames[i];
    }

    RecentProjectsManagerBase manager = RecentProjectsManagerBase.getInstanceEx();

    for (IdeFrame frame : orderedFrames) {
      Project project = frame.getProject();
      if (project != null) {
        String path = manager.getProjectPath(project);
        if (path != null) {
          manager.markPathRecent(path, project);
        }
      }
    }
  }
}