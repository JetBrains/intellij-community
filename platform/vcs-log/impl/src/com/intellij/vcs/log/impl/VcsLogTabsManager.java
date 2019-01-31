// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.visible.filters.VcsLogFiltersKt;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VcsLogTabsManager {
  @NotNull private final Project myProject;
  @NotNull private final VcsLogProjectTabsProperties myUiProperties;
  private boolean myIsLogDisposing = false;

  public VcsLogTabsManager(@NotNull Project project,
                           @NotNull MessageBus messageBus,
                           @NotNull VcsLogProjectTabsProperties uiProperties,
                           @NotNull Disposable parent) {
    myProject = project;
    myUiProperties = uiProperties;

    messageBus.connect(parent).subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, new VcsProjectLog.ProjectLogListener() {
      @Override
      public void logCreated(@NotNull VcsLogManager manager) {
        myIsLogDisposing = false;
        createLogTabs(manager);
      }

      @Override
      public void logDisposed(@NotNull VcsLogManager manager) {
        myIsLogDisposing = true;
      }
    });
  }

  @CalledInAwt
  private void createLogTabs(@NotNull VcsLogManager manager) {
    List<String> tabIds = myUiProperties.getTabs();
    for (String tabId : tabIds) {
      openLogTab(manager, tabId, false, false);
    }
  }

  // for statistics
  @NotNull
  public List<String> getTabs() {
    return myUiProperties.getTabs();
  }

  public void openAnotherLogTab(@NotNull VcsLogManager manager) {
    openAnotherLogTab(manager, false);
  }

  @NotNull
  public VcsLogUiImpl openAnotherLogTab(@NotNull VcsLogManager manager, boolean resetFilters) {
    return openLogTab(manager, VcsLogContentUtil.generateTabId(myProject), true, resetFilters);
  }

  @NotNull
  private VcsLogUiImpl openLogTab(@NotNull VcsLogManager manager, @NotNull String tabId, boolean focus, boolean resetFilters) {
    if (resetFilters) myUiProperties.resetState(tabId);

    VcsLogManager.VcsLogUiFactory<? extends VcsLogUiImpl> factory = new PersistentVcsLogUiFactory(manager.getMainLogUiFactory(tabId));
    VcsLogUiImpl ui = VcsLogContentUtil.openLogTab(myProject, manager, VcsLogContentProvider.TAB_NAME, tabId, factory, focus);
    updateTabName(ui);
    ui.addFilterListener(() -> updateTabName(ui));
    return ui;
  }

  private void updateTabName(@NotNull VcsLogUiImpl ui) {
    VcsLogContentUtil.renameLogUi(myProject, ui, generateDisplayName(ui));
  }

  @NotNull
  private static String generateDisplayName(@NotNull VcsLogUiImpl ui) {
    VcsLogFilterCollection filters = ui.getFilterUi().getFilters();
    if (filters.isEmpty()) return "all";
    return StringUtil.shortenTextWithEllipsis(VcsLogFiltersKt.getPresentation(filters), 150, 20);
  }

  private class PersistentVcsLogUiFactory implements VcsLogManager.VcsLogUiFactory<VcsLogUiImpl> {
    private final VcsLogManager.VcsLogUiFactory<? extends VcsLogUiImpl> myFactory;

    PersistentVcsLogUiFactory(@NotNull VcsLogManager.VcsLogUiFactory<? extends VcsLogUiImpl> factory) {
      myFactory = factory;
    }

    @Override
    public VcsLogUiImpl createLogUi(@NotNull Project project,
                                    @NotNull VcsLogData logData) {
      VcsLogUiImpl ui = myFactory.createLogUi(project, logData);
      myUiProperties.addTab(ui.getId());
      Disposer.register(ui, () -> {
        if (Disposer.isDisposing(myProject) || myIsLogDisposing) return; // need to restore the tab after project/log is recreated

        myUiProperties.removeTab(ui.getId()); // tab is closed by a user
      });
      return ui;
    }
  }
}
