// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.ui.tabs.ProjectTabsComponent;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implements the project tabs with the native tab group of macOS.
 */
@ApiStatus.Internal
public final class WindowTabsComponent extends ProjectTabsComponent {
  public WindowTabsComponent(@NotNull IdeFrameImpl nativeWindow, @Nullable Project project, @NotNull Disposable parentDisposable) {
    super(nativeWindow, project, parentDisposable);
  }

  @Override
  protected void fillPopupActions(@NotNull DefaultActionGroup group) {
    super.fillPopupActions(group);

    group.add(new DumbAwareAction(IdeBundle.message("mac.window.tabs.move.tab")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        moveTabToNewWindow(getPopupFrame(e));
      }
    });

    group.add(new DumbAwareAction(IdeBundle.message("mac.window.tabs.show.all.tabs")) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        showAllTabs(getPopupFrame(e));
      }
    });
  }

  @Override
  protected void selectTab(@NotNull IdeFrameImpl tabFrame) {
    Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(tabFrame);
      ID tabGroup = Foundation.invoke(window, "tabGroup");
      Foundation.invoke(tabGroup, "setSelectedWindow:", window);
    });
  }

  @Override
  protected void performCloseTab(@NotNull IdeFrameImpl tabFrame, boolean closeOthers) {
    Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(tabFrame);
      Foundation.invoke(window, closeOthers ? "performCloseOtherTabs:" : "performClose:", ID.NIL);
    });
  }

  private static void showAllTabs(@NotNull IdeFrameImpl tabFrame) {
    Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(tabFrame);
      ID tabs = Foundation.invoke(window, "tabbedWindows");
      int count = Foundation.invoke(tabs, "count").intValue();

      for (int i = 0; i < count; i++) {
        ID tab = Foundation.invoke(tabs, "objectAtIndex:", i);
        Foundation.invoke(tab, "setTabOverviewVisible:", 1);
      }
    });
  }

  @Override
  protected void moveTabToNewWindow(@NotNull IdeFrameImpl tabFrame) {
    Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(tabFrame);
      ID tabGroup = Foundation.invoke(window, "tabGroup");
      Foundation.invoke(tabGroup, "setSelectedWindow:", window);
      Foundation.invoke(window, "moveTabToNewWindow:", ID.NIL);

      ApplicationManager.getApplication().invokeLater(() -> MacWinTabsHandlerV2.updateTabBarsAfterMove(tabFrame, null, -1));
    });
  }

  @Override
  protected void moveTabToIndex(@NotNull IdeFrameImpl tabFrame, int index) {
    Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(tabFrame);
      ID tabGroup = Foundation.invoke(window, "tabGroup");
      Foundation.invoke(tabGroup, "removeWindow:", window);
      Foundation.invoke(tabGroup, "insertWindow:atIndex:", window, index);
      Foundation.invoke(tabGroup, "setSelectedWindow:", window);
    });
  }

  @Override
  protected void insertTab(@NotNull IdeFrameImpl tabFrame, int index) {
    Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(ownerFrame);
      ID tabGroup = Foundation.invoke(window, "tabGroup");
      ID tab = MacUtil.getWindowFromJavaWindow(tabFrame);
      Foundation.invoke(tabGroup, "insertWindow:atIndex:", tab, index);
      Foundation.invoke(tabGroup, "setSelectedWindow:", tab);

      ApplicationManager.getApplication().invokeLater(() -> MacWinTabsHandlerV2.updateTabBarsAfterMove(tabFrame, ownerFrame, index));
    });
  }

  @Override
  protected boolean isSelectedInTabGroup() {
    ID window = MacUtil.getWindowFromJavaWindow(ownerFrame);
    return Foundation.invoke(window, "isNativeSelected").intValue() == 1;
  }

  @Override
  protected @Nullable WindowTabsComponent findTabsComponent(@NotNull IdeFrameImpl frame) {
    return MacWinTabsHandlerV2.getTabsComponent(MacWinTabsHandlerV2.getTabsContainer(frame));
  }

  static void registerFrameDockContainer(@NotNull IdeFrameImpl frame, @NotNull CoroutineScope coroutineScope) {
    registerFrameDockContainer(frame, coroutineScope, () -> MacWinTabsHandlerV2.isTabsNotVisible(frame),
                               tabFrame -> moveTabToWindow(tabFrame, frame));
  }

  private static void moveTabToWindow(@NotNull IdeFrameImpl tabFrame, @NotNull IdeFrameImpl target) {
    Foundation.executeOnMainThread(true, false, () -> {
      ID window = MacUtil.getWindowFromJavaWindow(target);
      ID tabGroup = Foundation.invoke(window, "tabGroup");
      ID tab = MacUtil.getWindowFromJavaWindow(tabFrame);
      Foundation.invoke(tabGroup, "addWindow:", tab);
      Foundation.invoke(tabGroup, "setSelectedWindow:", tab);

      ApplicationManager.getApplication().invokeLater(() -> MacWinTabsHandlerV2.updateTabBarsAfterMove(tabFrame, target, -1));
    });
  }
}