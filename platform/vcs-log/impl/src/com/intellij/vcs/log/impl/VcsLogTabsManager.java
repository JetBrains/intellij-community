// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBus;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VcsLogTabsManager {
  @NotNull private final Project myProject;
  private boolean myIsLogDisposing = false;

  public VcsLogTabsManager(@NotNull Project project,
                           @NotNull MessageBus messageBus,
                           @NotNull Disposable parent) {
    myProject = project;

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
    List<String> tabIds = manager.getUiProperties().getTabs();
    for (String tabId : tabIds) {
      openLogTab(manager, tabId, false);
    }
  }

  public void openAnotherLogTab(@NotNull VcsLogManager manager) {
    openLogTab(manager, VcsLogContentUtil.generateTabId(myProject), true);
  }

  private void openLogTab(@NotNull VcsLogManager manager, @NotNull String tabId, boolean focus) {
    VcsLogManager.VcsLogUiFactory<? extends VcsLogUiImpl> factory =
      new PersistentVcsLogUiFactory(manager.getMainLogUiFactory(tabId), manager.getUiProperties());
    VcsLogContentUtil.openLogTab(myProject, manager, VcsLogContentProvider.TAB_NAME, tabId, factory, focus);
  }

  private class PersistentVcsLogUiFactory implements VcsLogManager.VcsLogUiFactory<VcsLogUiImpl> {
    private final VcsLogManager.VcsLogUiFactory<? extends VcsLogUiImpl> myFactory;
    @NotNull private final VcsLogTabsProperties myUiProperties;

    public PersistentVcsLogUiFactory(@NotNull VcsLogManager.VcsLogUiFactory<? extends VcsLogUiImpl> factory,
                                     @NotNull VcsLogTabsProperties properties) {
      myFactory = factory;
      myUiProperties = properties;
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
