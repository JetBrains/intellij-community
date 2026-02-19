// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import static git4idea.GitUtil.getRepositoryManager;

public abstract class GitAbstractRebaseAction extends GitOperationActionBase {
  protected GitAbstractRebaseAction() {
    super(Repository.State.REBASING);
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    if (getRepositoryManager(project).hasOngoingRebase()) {
      ProgressManager.getInstance().run(new Task.Backgroundable(project, getProgressTitle()) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          performActionForProject(project, indicator);
        }
      });
    }
    else {
      super.actionPerformed(e);
    }
  }

  @Override
  public void performInBackground(@NotNull GitRepository repositoryToOperate) {
    ProgressManager.getInstance().run(new Task.Backgroundable(repositoryToOperate.getProject(), getProgressTitle()) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        performActionForRepository(repositoryToOperate.getProject(), repositoryToOperate, indicator);
      }
    });
  }

  @Override
  protected @NotNull String getOperationName() {
    return GitBundle.message("action.Git.Rebase.operation.name");
  }

  protected abstract @NlsContexts.ProgressTitle @NotNull String getProgressTitle();

  protected abstract void performActionForProject(@NotNull Project project, @NotNull ProgressIndicator indicator);

  protected abstract void performActionForRepository(@NotNull Project project,
                                                     @NotNull GitRepository repository,
                                                     @NotNull ProgressIndicator indicator);
}
