// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import static git4idea.GitUtil.getRepositories;
import static git4idea.fetch.GitFetchSupport.fetchSupport;

public class GitFetch extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
    else {
      boolean hasRemotes = getRepositories(project).stream().anyMatch(repository -> !repository.getRemotes().isEmpty());
      e.getPresentation().setEnabledAndVisible(hasRemotes);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    GitVcs.runInBackground(new Task.Backgroundable(project, "Fetching...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        fetchSupport(project).fetchAllRemotes(getRepositories(project)).showNotification();
      }
    });
  }
}
