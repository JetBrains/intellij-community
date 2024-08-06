// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.IdeGlassPaneImplKt;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ArrayUtil;
import kotlin.Unit;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Alexander Lobas
 */
public final class MacWinTabsHandlerV2 extends MacWinTabsHandler {
  private static final String WINDOW_TABS_CONTAINER = "WINDOW_TABS_CONTAINER_KEY";

  static @NotNull JComponent _createAndInstallHandlerComponent(@NotNull JRootPane rootPane) {
    JPanel tabsContainer = new NonOpaquePanel(new BorderLayout());
    tabsContainer.setVisible(false);

    rootPane.putClientProperty(WINDOW_TABS_CONTAINER, tabsContainer);
    rootPane.putClientProperty("Window.transparentTitleBarHeight", 28);
    return tabsContainer;
  }

  static void _fastInit(@NotNull IdeFrameImpl frame) {
    if (JdkEx.setTabbingMode(frame, getWindowId(), null)) {
      Foundation.invoke("NSWindow", "setAllowsAutomaticWindowTabbing:", true);
    }
  }

  public MacWinTabsHandlerV2(@NotNull IdeFrameImpl frame, @NotNull CoroutineScope coroutineScope) {
    super(frame, coroutineScope);
  }

  @Override
  protected boolean initFrame(@NotNull IdeFrameImpl frame, @NotNull CoroutineScope coroutineScope) {
    boolean allowed = JdkEx.setTabbingMode(frame, getWindowId(), null);

    if (allowed) {
      Foundation.invoke("NSWindow", "setAllowsAutomaticWindowTabbing:", true);
      IdeGlassPaneImplKt.executeOnCancelInEdt(coroutineScope, () -> {
        updateTabBars(false);
        return Unit.INSTANCE;
      });
      WindowTabsComponent.registerFrameDockContainer(frame, coroutineScope);
    }

    return allowed;
  }

  @Override
  public void setProject() {
    // update tab logic only after call [NSWindow makeKeyAndOrderFront] and after add frame to window manager
    ApplicationManager.getApplication().invokeLater(() -> updateTabBars(true));
  }

  @Override
  public void enteringFullScreen() {
  }

  @Override
  public void enterFullScreen() {
    if (!myFrame.isDisplayable() || !myFrame.isShowing()) {
      return;
    }
    Point locationOnScreen = myFrame.getLocationOnScreen();
    if (myFrame.getWidth() == 0 || myFrame.getHeight() == 0 || locationOnScreen.x > 0 || locationOnScreen.y > 0) {
      handleFullScreenResize(myFrame);
    }
  }

  @Override
  public void exitFullScreen() {
  }

  private static final class TabsInfo {
    final IdeFrameImpl @NotNull [] frames;
    final @NotNull Map<IdeFrameImpl, ProjectFrameHelper> helpersMap;

    private TabsInfo(IdeFrameImpl @NotNull [] frames, @NotNull Map<IdeFrameImpl, ProjectFrameHelper> helpersMap) {
      this.frames = frames;
      this.helpersMap = helpersMap;
    }
  }

  private static @Nullable TabsInfo getTabsInfo(IdeFrame @NotNull [] helpers, @NotNull IdeFrameImpl window) {
    int allLength = helpers.length;
    if (allLength < 2) {
      return null;
    }

    ID tabs = Foundation.invoke(MacUtil.getWindowFromJavaWindow(window), "tabbedWindows");
    int length = Foundation.invoke(tabs, "count").intValue();
    if (length < 2) {
      return null; // no tabs
    }

    if (allLength < length) {
      return null; // early state
    }

    Map<IdeFrameImpl, ProjectFrameHelper> helpersMap = new HashMap<>();
    ID[] allWindows = new ID[allLength];
    IdeFrameImpl[] allFrames = new IdeFrameImpl[allLength];

    for (int i = 0; i < allLength; i++) {
      ProjectFrameHelper helper = (ProjectFrameHelper)helpers[i];
      IdeFrameImpl frame = helper.getFrame();
      helpersMap.put(frame, helper);
      allFrames[i] = frame;
      allWindows[i] = MacUtil.getWindowFromJavaWindow(frame);
    }

    IdeFrameImpl[] frames = new IdeFrameImpl[length];

    for (int i = 0, founded = 0; i < allLength && founded < length; i++) {
      int index = Foundation.invoke(tabs, "indexOfObject:", allWindows[i]).intValue();
      if (index != -1) {
        frames[index] = allFrames[i];
        founded++;
      }
    }

    return new TabsInfo(frames, helpersMap);
  }

  private void updateTabBars(boolean create) {
    IdeFrame[] helpers = WindowManager.getInstance().getAllProjectFrames();

    if (create) {
      TabsInfo info = getTabsInfo(helpers, myFrame);
      if (info == null) {
        return;
      }

      int index = ArrayUtil.indexOfIdentity(info.frames, myFrame);

      for (IdeFrameImpl frame : info.frames) {
        if (frame == myFrame || isTabsNotVisible(frame)) {
          createTabBarsForFrame(frame, info.helpersMap.get(frame), info.frames);
        }
        else {
          insertTabForFrame(frame, myFrame, index);
        }
      }
    }
    else {
      for (IdeFrame _helper : helpers) {
        ProjectFrameHelper helper = (ProjectFrameHelper)_helper;
        if (helper.isDisposed$intellij_platform_ide_impl()) {
          continue;
        }

        IdeFrameImpl frame = helper.getFrame();
        if (frame == myFrame) {
          continue;
        }
        removeFromFrame(frame, myFrame);
      }
    }
  }

  private static void createTabBarsForFrame(@NotNull IdeFrameImpl frame,
                                            @NotNull ProjectFrameHelper helper,
                                            IdeFrameImpl @NotNull [] tabFrames) {
    WindowTabsComponent tabs = new WindowTabsComponent(frame, helper.getProject(), helper.createDisposable$intellij_platform_ide_impl());

    JPanel parentComponent = getTabsContainer(frame);
    parentComponent.add(tabs);
    tabs.createTabsForFrame(tabFrames);

    boolean oldVisible = parentComponent.isVisible();
    parentComponent.setVisible(true);

    Container parent = parentComponent.getParent();
    if (parent == null || oldVisible) {
      return;
    }
    parent.doLayout();
    parent.revalidate();
    parent.repaint();
  }

  private static void insertTabForFrame(@NotNull IdeFrameImpl frame, @NotNull IdeFrameImpl tab, int index) {
    Objects.requireNonNull(getTabsComponent(getTabsContainer(frame))).insertTabForFrame(tab, index);
  }

  private static void removeFromFrame(@NotNull IdeFrameImpl frame, @Nullable IdeFrameImpl tab) {
    JPanel parentComponent = getTabsContainer(frame);
    WindowTabsComponent tabs = getTabsComponent(parentComponent);
    if (tabs == null) {
      return; // no tabs
    }

    if (tab == null || tabs.removeTabFromFrame(tab)) {
      parentComponent.remove(tabs);
      parentComponent.setVisible(false);
      tabs.selfDispose();

      Container parent = parentComponent.getParent();
      if (parent != null) {
        parent.doLayout();
        parent.revalidate();
        parent.repaint();
      }
    }
  }

  static boolean isTabsNotVisible(IdeFrameImpl frame) {
    return getTabsComponent(getTabsContainer(frame)) == null;
  }

  static @NotNull JPanel getTabsContainer(@NotNull IdeFrameImpl frame) {
    return (JPanel)frame.getRootPane().getClientProperty(WINDOW_TABS_CONTAINER);
  }

  static @Nullable WindowTabsComponent getTabsComponent(JPanel parentComponent) {
    return parentComponent.getComponentCount() != 1 ? null : (WindowTabsComponent)parentComponent.getComponent(0);
  }

  static void updateTabBarsAfterMerge() {
    IdeFrame[] helpers = WindowManager.getInstance().getAllProjectFrames();
    if (helpers.length == 0) {
      return;
    }

    for (IdeFrame helper : helpers) {
      removeFromFrame(((ProjectFrameHelper)helper).getFrame(), null);
    }

    TabsInfo info = Objects.requireNonNull(getTabsInfo(helpers, ((ProjectFrameHelper)helpers[0]).getFrame()));

    for (IdeFrameImpl frame : info.frames) {
      createTabBarsForFrame(frame, info.helpersMap.get(frame), info.frames);
    }
  }

  static void updateTabBarsAfterMove(@NotNull IdeFrameImpl movedFrame, @Nullable IdeFrameImpl target, int index) {
    IdeFrame[] helpers = WindowManager.getInstance().getAllProjectFrames();

    for (IdeFrame helper : helpers) {
      IdeFrameImpl frame = ((ProjectFrameHelper)helper).getFrame();
      if (frame != movedFrame && frame != target) {
        removeFromFrame(frame, movedFrame);
      }
    }

    removeFromFrame(movedFrame, null);

    if (target == null) {
      return;
    }

    TabsInfo info = Objects.requireNonNull(getTabsInfo(helpers, target));

    for (IdeFrameImpl frame : info.frames) {
      if (frame == movedFrame || index == -1) {
        createTabBarsForFrame(frame, info.helpersMap.get(frame), info.frames);
      }
      else {
        insertTabForFrame(frame, movedFrame, index);
      }
    }
  }
}