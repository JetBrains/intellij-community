// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.impl.VcsGoToRefComparator;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsLogNavigationUtil;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.VcsLogUiEx;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@ApiStatus.Internal
public class GoToHashOrRefAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    VcsLogUiEx logUi = e.getData(VcsLogInternalDataKeys.LOG_UI_EX);
    if (logUi == null) return;
    VcsLogManager logManager = e.getData(VcsLogInternalDataKeys.LOG_MANAGER);
    if (logManager == null) return;

    Set<VirtualFile> visibleRoots = VcsLogUtil.getVisibleRoots(logUi);
    GoToHashOrRefPopup popup = new GoToHashOrRefPopup(project, logUi.getDataPack().getRefs(), visibleRoots,
                                                      hash -> VcsLogNavigationUtil.jumpToRefOrHash(logUi, hash, false, true),
                                                      reference -> VcsLogNavigationUtil.jumpToCommit(logUi, reference.getCommitHash(),
                                                                                                     reference.getRoot(), false, true),
                                                      logManager.getColorManager(),
                                                      new VcsGoToRefComparator(logUi.getDataPack().getLogProviders()));
    popup.show(VcsLogUiUtil.getComponent(logUi));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VcsLogUiEx logUi = e.getData(VcsLogInternalDataKeys.LOG_UI_EX);
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && logUi != null);
  }
}
