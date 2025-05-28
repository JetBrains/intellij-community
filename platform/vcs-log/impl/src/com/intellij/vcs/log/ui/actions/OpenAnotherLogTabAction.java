// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.impl.*;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    VcsLogManager logManager = getMainLogManager(e);
    if (logManager == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    String vcsName = VcsLogUtil.getVcsDisplayName(project, logManager);
    e.getPresentation().setText(getText(vcsName));
    e.getPresentation().setDescription(getDescription(vcsName));
  }

  private static @Nullable IdeVcsLogManager getMainLogManager(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return null;
    VcsProjectLog projectLog = VcsProjectLog.getInstance(project);
    VcsLogManager mainLogManager = projectLog.getLogManager();
    if (!(mainLogManager instanceof IdeVcsLogManager actualManager)) return null;
    // only for main log (it is a question, how and where we want to open tabs for external logs)
    VcsLogManager dataLogManager = e.getData(VcsLogInternalDataKeys.LOG_MANAGER);
    if (dataLogManager != null && dataLogManager != actualManager) return null;
    return actualManager;
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
    IdeVcsLogManager logManager = getMainLogManager(e);
    if (logManager == null) return;

    VcsLogFilterCollection filters;
    if (Registry.is("vcs.log.copy.filters.to.new.tab")) {
      filters = getFilters(project, e);
    }
    else {
      filters = VcsLogFilterObject.collection();
    }
    logManager.openNewLogTab(getLocation(e), filters);
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
      ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
      if (toolWindow == null) return VcsLogFilterObject.collection();

      VcsLogUi logUi = VcsLogContentUtil.findSelectedLogUi(toolWindow);
      if (logUi == null) return VcsLogFilterObject.collection();

      return logUi.getFilterUi().getFilters();
    }
  }

  @ApiStatus.Internal
  public static class InEditor extends OpenAnotherLogTabAction {
    @Override
    public void update(@NotNull AnActionEvent e) {
      if (Registry.is("toolwindow.open.tab.in.editor")) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
      super.update(e);
      if (e.getData(PlatformDataKeys.TOOL_WINDOW) != null && ActionPlaces.VCS_LOG_TOOLBAR_PLACE.equals(e.getPlace())) {
        e.getPresentation().setEnabledAndVisible(false);
      }
    }

    @Override
    protected @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription(@Nls @NotNull String vcsName) {
      return VcsLogBundle.message("vcs.log.action.description.open.new.tab.with.log.in.editor", vcsName);
    }

    @Override
    protected @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getText(@Nls @NotNull String vcsName) {
      return VcsLogBundle.message("vcs.log.action.open.new.tab.with.log.in.editor", vcsName);
    }

    @Override
    protected @NotNull VcsLogTabLocation getLocation(@NotNull AnActionEvent e) {
      return VcsLogTabLocation.EDITOR;
    }
  }
}
