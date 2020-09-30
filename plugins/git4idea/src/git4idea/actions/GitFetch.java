// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import static git4idea.GitUtil.getRepositories;
import static git4idea.fetch.GitFetchSupport.fetchSupport;
import static git4idea.ui.branch.GitBranchActionsUtilKt.hasRemotes;

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

public class GitFetch extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      e.getPresentation().setEnabled(hasRemotes(project));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    GitVcs.runInBackground(new Task.Backgroundable(project, GitBundle.message("fetching"), true) {
      GitFetchResult result;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        result = fetchSupport(project).fetchAllRemotes(getRepositories(project));
      }

      @Override
      public void onFinished() {
        if (result != null) {
          onFetchFinished(result);
        }
      }
    });
  }

  @RequiresEdt
  protected void onFetchFinished(@NotNull GitFetchResult result) {
    result.showNotification();
  }
}
