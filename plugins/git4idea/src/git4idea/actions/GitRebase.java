// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseDialog;
import git4idea.rebase.GitRebaseUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.intellij.dvcs.DvcsUtil.sortRepositories;
import static git4idea.GitUtil.*;
import static git4idea.rebase.GitRebaseUtils.getRebasingRepositories;
import static java.util.Collections.singletonList;

public class GitRebase extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();
    if (project == null || !hasGitRepositories(project) || !getRebasingRepositories(project).isEmpty()) {
      presentation.setEnabledAndVisible(false);
    }
    else if (getRepositoriesInState(project, Repository.State.NORMAL).isEmpty()) {
      presentation.setEnabled(false);
    }
    else {
      presentation.setEnabledAndVisible(true);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    ArrayList<GitRepository> repositories = new ArrayList<>(getRepositories(project));
    repositories.removeAll(getRebasingRepositories(project));
    List<VirtualFile> roots = new ArrayList<>(getRootsFromRepositories(sortRepositories(repositories)));
    GitRepository selectedRepo = GitBranchUtil.guessRepositoryForOperation(project, e.getDataContext());
    VirtualFile defaultRoot = selectedRepo != null ? selectedRepo.getRoot() : null;
    final GitRebaseDialog dialog = new GitRebaseDialog(project, roots, defaultRoot);
    if (dialog.showAndGet()) {
      VirtualFile root = dialog.gitRoot();
      GitRebaseParams selectedParams = dialog.getSelectedParams();
      ProgressManager.getInstance().run(new Task.Backgroundable(project, GitBundle.message("rebase.progress.indicator.title")) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          GitRepository selectedRepository =
            Objects.requireNonNull(GitRepositoryManager.getInstance(project).getRepositoryForRoot(root));
          GitRebaseUtils.rebase(project, singletonList(selectedRepository), selectedParams, indicator);
        }
      });
    }
  }
}
