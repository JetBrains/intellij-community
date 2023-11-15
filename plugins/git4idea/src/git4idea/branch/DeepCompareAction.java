// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.MainVcsLogUi;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import com.intellij.vcs.log.ui.VcsLogUiEx;
import com.intellij.vcs.log.ui.filter.BranchPopupBuilder;
import com.intellij.vcs.log.util.VcsLogUiUtil;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import com.intellij.vcs.log.visible.filters.VcsLogFiltersKt;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Set;

public class DeepCompareAction extends ToggleAction implements DumbAware {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    MainVcsLogUi ui = e.getData(VcsLogInternalDataKeys.MAIN_UI);
    VcsLogData dataProvider = e.getData(VcsLogInternalDataKeys.LOG_DATA);
    if (project == null || dataProvider == null || ui == null) {
      return false;
    }
    return DeepComparator.getInstance(project, dataProvider, ui).hasHighlightingOrInProgress();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean selected) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    MainVcsLogUi ui = e.getData(VcsLogInternalDataKeys.MAIN_UI);
    VcsLogData dataProvider = e.getData(VcsLogInternalDataKeys.LOG_DATA);
    if (project == null || dataProvider == null || ui == null) {
      return;
    }

    final DeepComparator dc = DeepComparator.getInstance(project, dataProvider, ui);
    if (selected) {
      VcsLogUsageTriggerCollector.triggerUsage(e, this);

      VcsLogDataPack dataPack = ui.getDataPack();
      String singleBranchName = DeepComparator.getComparedBranchFromFilters(ui.getFilterUi().getFilters(), dataPack.getRefs());
      if (singleBranchName == null) {
        selectBranchAndPerformAction(ui, e, selectedBranch -> {
          VcsLogFilterCollection collection = ui.getFilterUi().getFilters();
          collection = VcsLogFiltersKt.without(collection, VcsLogBranchLikeFilter.class);
          collection = VcsLogFiltersKt.with(collection, VcsLogFilterObject.fromBranch(selectedBranch));
          ui.getFilterUi().setFilters(collection);
          dc.startTask(dataPack, selectedBranch);
        }, getGitRoots(project, ui));
        return;
      }
      dc.startTask(dataPack, singleBranchName);
    }
    else {
      dc.stopTaskAndUnhighlight();
    }
  }

  private static void selectBranchAndPerformAction(@NotNull VcsLogUiEx ui,
                                                   @NotNull AnActionEvent event,
                                                   @NotNull Consumer<? super String> consumer,
                                                   @NotNull Collection<? extends VirtualFile> visibleRoots) {
    VcsLogDataPack dataPack = ui.getDataPack();
    ActionGroup actionGroup = new BranchPopupBuilder(dataPack, visibleRoots, null) {
      @Override
      protected @NotNull AnAction createAction(@NotNull @NlsActions.ActionText String name, @NotNull Collection<? extends VcsRef> refs) {
        return new DumbAwareAction(name) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            consumer.consume(name);
          }
        };
      }
    }.build();
    ListPopup popup =
      JBPopupFactory.getInstance().createActionGroupPopup(GitBundle.message("git.log.cherry.picked.highlighter.select.branch.popup"),
                                                          actionGroup, event.getDataContext(),
                                                          false, false, false,
                                                          null, -1, null);
    InputEvent inputEvent = event.getInputEvent();
    if (inputEvent instanceof MouseEvent) {
      popup.show(new RelativePoint((MouseEvent)inputEvent));
    }
    else {
      popup.showInCenterOf(VcsLogUiUtil.getComponent(ui));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getData(CommonDataKeys.PROJECT);
    VcsLogUi ui = e.getData(VcsLogDataKeys.VCS_LOG_UI);
    if (project == null || ui == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      Set<VirtualFile> visibleRoots = VcsLogUtil.getVisibleRoots(ui);
      Set<VirtualFile> allRoots = visibleRoots;
      if (allRoots.isEmpty()) {
        allRoots = ContainerUtil.map2Set(ProjectLevelVcsManager.getInstance(project).getAllVcsRoots(), VcsRoot::getPath);
      }
      e.getPresentation().setEnabled(hasGitRoots(project, visibleRoots));
      e.getPresentation().setVisible(hasGitRoots(project, allRoots));
    }
  }

  private static @NotNull Collection<VirtualFile> getGitRoots(@NotNull Project project, @NotNull VcsLogUi ui) {
    return ContainerUtil.filter(VcsLogUtil.getVisibleRoots(ui), root -> isGitRoot(project, root));
  }

  private static boolean hasGitRoots(@NotNull Project project, @NotNull Set<? extends VirtualFile> roots) {
    return ContainerUtil.exists(roots, root -> isGitRoot(project, root));
  }

  private static boolean isGitRoot(@NotNull Project project, @NotNull VirtualFile root) {
    return GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root) != null;
  }
}
