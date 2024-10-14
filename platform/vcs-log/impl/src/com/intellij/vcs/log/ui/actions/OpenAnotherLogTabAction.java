// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsLogTabLocation;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public abstract class OpenAnotherLogTabAction extends DumbAwareAction {

  protected OpenAnotherLogTabAction() {
    getTemplatePresentation().setText(() -> getText(VcsLogBundle.message("vcs")));
    getTemplatePresentation().setDescription(() -> getDescription(VcsLogBundle.message("vcs")));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    VcsProjectLog projectLog = VcsProjectLog.getInstance(project);
    VcsLogManager logManager = ObjectUtils.chooseNotNull(e.getData(VcsLogInternalDataKeys.LOG_MANAGER), projectLog.getLogManager());
    if (logManager == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    // only for main log (it is a question, how and where we want to open tabs for external logs)
    e.getPresentation().setEnabledAndVisible(projectLog.getLogManager() == logManager);

    String vcsName = VcsLogUtil.getVcsDisplayName(project, logManager);
    e.getPresentation().setText(getText(vcsName));
    e.getPresentation().setDescription(getDescription(vcsName));
  }

  protected @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription(@Nls @NotNull String vcsName) {
    return VcsLogBundle.message("vcs.log.action.description.open.new.tab.with.log", vcsName);
  }

  protected @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getText(@Nls @NotNull String vcsName) {
    return VcsLogBundle.message("vcs.log.action.open.new.tab.with.log", vcsName);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    VcsLogFilterCollection filters;
    if (Registry.is("vcs.log.copy.filters.to.new.tab")) {
      filters = getFilters(project, e);
    }
    else {
      filters = VcsLogFilterObject.collection();
    }
    VcsProjectLog.getInstance(project).openLogTab(filters, getLocation(e));
  }

  protected @NotNull VcsLogFilterCollection getFilters(@NotNull Project project, @NotNull AnActionEvent e) {
    VcsLogUi logUi = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    if (logUi == null) return VcsLogFilterObject.collection();
    return logUi.getFilterUi().getFilters();
  }

  protected abstract @NotNull VcsLogTabLocation getLocation(@NotNull AnActionEvent e);

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @ApiStatus.Internal
  public static class InToolWindow extends OpenAnotherLogTabAction {
    @Override
    protected @NotNull VcsLogTabLocation getLocation(@NotNull AnActionEvent e) {
      return VcsLogTabLocation.TOOL_WINDOW;
    }

    @Override
    protected @NotNull VcsLogFilterCollection getFilters(@NotNull Project project, @NotNull AnActionEvent e) {
      VcsLogManager logManager = VcsProjectLog.getInstance(project).getLogManager();
      if (logManager == null) return VcsLogFilterObject.collection();

      Collection<? extends VcsLogUi> uis = ContainerUtil.filterIsInstance(
        logManager.getVisibleLogUis(VcsLogTabLocation.TOOL_WINDOW),
        MainVcsLogUi.class);
      if (uis.isEmpty()) return VcsLogFilterObject.collection();
      return ContainerUtil.getFirstItem(uis).getFilterUi().getFilters();
    }
  }
}
