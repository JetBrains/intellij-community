// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.ui.branch.popup.GitBranchesTreePopup;
import org.jetbrains.annotations.NotNull;

/**
 * <p>
 * Invokes a {@link GitBranchPopup} to checkout and control Git branches.
 * </p>
 */
public class GitBranchesAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    GitRepository repository = GitBranchUtil.guessRepositoryForOperation(project, e.getDataContext());
    if (repository == null) return;

    if (GitBranchesTreePopup.isEnabled()) {
      GitBranchesTreePopup.show(project, repository);
    }
    else {
      GitBranchPopup.getInstance(project, repository, e.getDataContext()).asListPopup().showCenteredInCurrentWindow(project);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(project != null && !project.isDisposed() &&
                                             !GitRepositoryManager.getInstance(project).getRepositories().isEmpty());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
