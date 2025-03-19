// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions.history;

import com.google.common.primitives.Ints;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsCommitMetadata;
import com.intellij.vcs.log.data.DataGetter;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.history.FileHistoryUi;
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector;
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

/**
 * @deprecated replaced by {@link FileHistoryOneCommitAction}
 */
@Deprecated(forRemoval = true)
public abstract class FileHistorySingleCommitAction<T extends VcsCommitMetadata> extends AnAction implements DumbAware {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    FileHistoryUi ui = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI);
    if (project == null || ui == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);

    List<T> details = getSelection(ui);
    if (details.isEmpty()) {
      e.getPresentation().setEnabled(false);
      return;
    }

    T detail = getFirstItem(details);
    if (detail instanceof LoadingDetails) detail = null;
    e.getPresentation().setEnabled(details.size() == 1 && isEnabled(ui, detail, e));
  }

  protected boolean isEnabled(@NotNull FileHistoryUi ui, @Nullable T detail, @NotNull AnActionEvent e) {
    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    VcsLogUsageTriggerCollector.triggerUsage(e, this);

    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    FileHistoryUi ui = e.getData(VcsLogInternalDataKeys.FILE_HISTORY_UI);
    if (ui == null) return;

    List<CommitId> commits = ui.getTable().getSelection().getCommits();
    if (commits.size() != 1) return;
    CommitId commit = Objects.requireNonNull(getFirstItem(commits));

    List<Integer> commitIndex = Ints.asList(ui.getLogData().getCommitIndex(commit.getHash(), commit.getRoot()));
    getDetailsGetter(ui).loadCommitsData(commitIndex, details -> {
      if (!details.isEmpty()) {
        performAction(project, ui, Objects.requireNonNull(getFirstItem(details)), e);
      }
    }, t -> FileHistoryOneCommitActionKt.showError(project, t), null);
  }

  protected abstract @NotNull List<T> getSelection(@NotNull FileHistoryUi ui);

  protected abstract @NotNull DataGetter<T> getDetailsGetter(@NotNull FileHistoryUi ui);

  protected abstract void performAction(@NotNull Project project,
                                        @NotNull FileHistoryUi ui,
                                        @NotNull T detail,
                                        @NotNull AnActionEvent e);
}
