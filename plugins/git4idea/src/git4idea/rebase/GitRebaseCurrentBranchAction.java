/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.rebase;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.ContainingBranchesGetter;
import com.intellij.vcs.log.data.VcsLogData;
import git4idea.GitRemoteBranch;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.config.GitSharedSettings;
import git4idea.repo.GitRepository;
import git4idea.reset.GitOneCommitPerRepoLogAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.Collections.singletonList;

/**
 * @author Dmitry Zhuravlev
 *         Date:  22.05.2017
 */
public class GitRebaseCurrentBranchAction extends GitOneCommitPerRepoLogAction {
  private static final String COMMIT_PUSHED_TO_PROTECTED = "The commit is already pushed to protected branch ";
  private static final String FAILURE_TITLE = "Can't Rebase to Commit";

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (!doActionIfCommitInProtectedBranch(e, (protectedBranch) -> {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setDescription(COMMIT_PUSHED_TO_PROTECTED + protectedBranch);
    })) {
      e.getPresentation().setEnabledAndVisible(true);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    if (!doActionIfCommitInProtectedBranch(e, (protectedBranch) -> Messages
      .showErrorDialog(project, COMMIT_PUSHED_TO_PROTECTED + protectedBranch, FAILURE_TITLE))) {
      super.actionPerformed(e);
    }
  }

  @Override
  protected void actionPerformed(@NotNull Project project, @NotNull Map<GitRepository, VcsFullCommitDetails> commits) {
    final GitRepository repository = GitBranchUtil.getCurrentRepository(project);
    final VcsFullCommitDetails commit = commits.get(repository);
    if (repository == null || commit == null) return;
    final String commitHashString = commit.getId().asString();
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Rebasing...") {
      public void run(@NotNull ProgressIndicator indicator) {
        GitRebaseUtils.rebase(project, singletonList(repository), new GitRebaseParams(null, null, commitHashString, true, true), indicator);
      }
    });
  }

  private boolean doActionIfCommitInProtectedBranch(@NotNull final AnActionEvent e, final Consumer<String> action){
    final Project project = e.getProject();
    final VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    if(project == null || log == null){
      return false;
    }
    final VcsLogData data = (VcsLogData)e.getRequiredData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER);
    final VcsShortCommitDetails commit = log.getSelectedShortDetails().get(0);
    final GitRepository repository = getRepositoryManager(project).getRepositoryForRootQuick(commit.getRoot());
    if (repository == null) {
      return false;
    }
    final VirtualFile root = commit.getRoot();
    final Hash hash = commit.getId();
    final List<String> branches = findContainingBranches(data, root, hash);

    //execute action for protected branches
    final String protectedBranch = findProtectedRemoteBranch(repository, branches);
    if (protectedBranch != null) {
      action.accept(protectedBranch);
      return true;
    }
    return false;
  }

  @Nullable
  private static String findProtectedRemoteBranch(@NotNull GitRepository repository, @NotNull List<String> branches) { //TODO extract to some common utils and use also in GitUncommitAction
    GitSharedSettings settings = GitSharedSettings.getInstance(repository.getProject());
    // protected branches hold patterns for branch names without remote names
    return repository.getBranches().getRemoteBranches().stream().
      filter(it -> settings.isBranchProtected(it.getNameForRemoteOperations())).
      map(GitRemoteBranch::getNameForLocalOperations).
      filter(branches::contains).
      findAny().
      orElse(null);
  }

  @NotNull
  private static List<String> findContainingBranches(@NotNull VcsLogData data, @NotNull VirtualFile root, @NotNull Hash hash) { //TODO extract to some common utils and use also in GitUncommitAction
    ContainingBranchesGetter branchesGetter = data.getContainingBranchesGetter();
    List<String> branches = branchesGetter.getContainingBranchesFromCache(root, hash);
    if (branches != null) return branches;
    return branchesGetter.getContainingBranchesSynchronously(root, hash);
  }
}
