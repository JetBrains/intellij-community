// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import git4idea.GitVcs;
import git4idea.fetch.GitFetchResult;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static git4idea.GitUtil.getRepositories;
import static git4idea.fetch.GitFetchSupport.fetchSupport;
import static git4idea.ui.branch.GitBranchActionsUtilKt.hasRemotes;

public class GitFetch extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    performUpdate(e);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    performFetch(project, result -> onFetchFinished(project, result));
  }

  @RequiresEdt
  protected void onFetchFinished(@NotNull Project project, @NotNull GitFetchResult result) {
    result.showNotification();
  }

  private static void performUpdate(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    if (!hasRemotes(project)) {
      e.getPresentation().setEnabled(false);
      return;
    }
    if (fetchSupport(project).isFetchRunning()) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setDescription(GitBundle.message("action.Git.Fetch.description.fetch.in.progress"));
      return;
    }

    e.getPresentation().setDescription("");
    e.getPresentation().setEnabledAndVisible(true);
  }

  @RequiresEdt
  private static void performFetch(@NotNull Project project, @Nullable Consumer<@NotNull GitFetchResult> onFetchFinished) {
    GitVcs.runInBackground(new Task.Backgroundable(project, GitBundle.message("fetching"), true) {
      GitFetchResult result;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        result = fetchSupport(project).fetchAllRemotes(getRepositories(project));
      }

      @Override
      public void onFinished() {
        if (onFetchFinished != null && result != null) {
          onFetchFinished.accept(result);
        }
      }
    });
  }
}
