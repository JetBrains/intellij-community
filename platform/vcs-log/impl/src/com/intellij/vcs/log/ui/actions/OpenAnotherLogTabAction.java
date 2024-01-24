// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsLogTabLocation;
import com.intellij.vcs.log.impl.VcsProjectLog;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class OpenAnotherLogTabAction extends DumbAwareAction {
  private final @NotNull VcsLogTabLocation myLocation;

  protected OpenAnotherLogTabAction(@NotNull VcsLogTabLocation location) {
    super(AllIcons.Actions.OpenNewTab);
    getTemplatePresentation().setText(() -> getText(VcsLogBundle.message("vcs")));
    getTemplatePresentation().setDescription(() -> getDescription(VcsLogBundle.message("vcs")));
    myLocation = location;
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

    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLogUi logUi = e.getData(VcsLogDataKeys.VCS_LOG_UI);

    VcsLogFilterCollection filters;
    if (Registry.is("vcs.log.copy.filters.to.new.tab") && logUi != null) {
      filters = logUi.getFilterUi().getFilters();
    }
    else {
      filters = VcsLogFilterObject.collection();
    }

    VcsProjectLog.getInstance(project).openLogTab(filters, myLocation);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public static class InToolWindow extends OpenAnotherLogTabAction {
    protected InToolWindow() {
      super(VcsLogTabLocation.TOOL_WINDOW);
    }
  }

  public static class InEditor extends OpenAnotherLogTabAction {
    protected InEditor() {
      super(VcsLogTabLocation.EDITOR);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabledAndVisible(false);
    }

    @Override
    protected @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription(@Nls @NotNull String vcsName) {
      return VcsLogBundle.message("vcs.log.action.description.open.new.tab.with.log.in.editor", vcsName);
    }

    @Override
    protected @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getText(@Nls @NotNull String vcsName) {
      return VcsLogBundle.message("vcs.log.action.open.new.tab.with.log.in.editor", vcsName);
    }
  }
}
