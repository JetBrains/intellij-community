// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseUtils;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static git4idea.GitNotificationIdsHolder.TAG_NOT_CREATED;

/**
 * Executes the logic of git branch operations.
 * All operations are run in the current thread.
 * All UI interaction is done via the {@link GitBranchUiHandler} passed to the constructor.
 */
public final class GitBranchWorker {

  private static final Logger LOG = Logger.getInstance(GitBranchWorker.class);

  private final @NotNull Project myProject;
  private final @NotNull Git myGit;
  private final @NotNull GitBranchUiHandler myUiHandler;
  private final @NotNull GitVcs myVcs;

  public GitBranchWorker(@NotNull Project project, @NotNull Git git, @NotNull GitBranchUiHandler uiHandler) {
    myProject = project;
    myGit = git;
    myUiHandler = uiHandler;
    myVcs = GitVcs.getInstance(myProject);
  }
  
  public void checkoutNewBranch(final @NotNull String name, @NotNull List<? extends GitRepository> repositories) {
    updateInfo(repositories);
    repositories = ContainerUtil.filter(repositories, repository -> {
      GitLocalBranch currentBranch = repository.getCurrentBranch();
      return currentBranch == null || !currentBranch.getName().equals(name);
    });
    if (!repositories.isEmpty()) {
      new GitCheckoutNewBranchOperation(myProject, myGit, myUiHandler, repositories, name).execute();
    }
    else {
      LOG.error("Creating new branch the same as current in all repositories: " + name);
    }
  }

  public void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints) {
    createBranch(name, startPoints, false);
  }

  public void createBranch(@NotNull String name, @NotNull Map<GitRepository, String> startPoints, boolean force) {
    updateInfo(startPoints.keySet());
    new GitCreateBranchOperation(myProject, myGit, myUiHandler, name, startPoints, force).execute();
  }

  public void createNewTag(@NotNull String name, @NotNull String reference, @NotNull List<? extends GitRepository> repositories) {
    for (GitRepository repository : repositories) {
      GitCommandResult result = myGit.createNewTag(repository, name, null, reference);
      repository.getRepositoryFiles().refreshTagsFiles();
      if (!result.success()) {
        String error = GitBundle.message("branch.worker.could.not.create.tag",
                                         name,
                                         GitUtil.getRepositoryManager(repository.getProject()).getRepositories().size(),
                                         getShortRepositoryName(repository));
        VcsNotifier.getInstance(myProject).notifyError(TAG_NOT_CREATED, error, result.getErrorOutputAsHtmlString(), true);
        break;
      }
    }
  }

  public void checkoutNewBranchStartingFrom(@NotNull String newBranchName, @NotNull String startPoint,
                                            @NotNull List<? extends GitRepository> repositories) {
    checkoutNewBranchStartingFrom(newBranchName, startPoint, false, repositories);
  }

  public void checkoutNewBranchStartingFrom(@NotNull String newBranchName, @NotNull String startPoint, boolean overwriteIfNeeded,
                                            @NotNull List<? extends GitRepository> repositories) {
    updateInfo(repositories);
    new GitCheckoutOperation(myProject, myGit, myUiHandler, repositories, startPoint, false, overwriteIfNeeded, true, newBranchName).execute();
  }

  public void checkout(final @NotNull String reference, boolean detach, @NotNull List<? extends GitRepository> repositories) {
    updateInfo(repositories);
    new GitCheckoutOperation(myProject, myGit, myUiHandler, repositories, reference, detach, false, false, null).execute();
  }


  public void deleteBranch(final @NotNull String branchName, final @NotNull List<? extends GitRepository> repositories) {
    updateInfo(repositories);
    new GitDeleteBranchOperation(myProject, myGit, myUiHandler, repositories, branchName).execute();
  }

  public void deleteTag(final @NotNull String tagName, final @NotNull List<? extends GitRepository> repositories) {
    updateInfo(repositories);
    new GitDeleteTagOperation(myProject, myGit, myUiHandler, repositories, tagName).execute();
  }

  public void deleteRemoteTag(final @NotNull String tagName, final @NotNull Map<GitRepository, String> repositories) {
    updateInfo(repositories.keySet());
    new GitDeleteRemoteTagOperation(myProject, myGit, myUiHandler, repositories, tagName).execute();
  }

  public void deleteRemoteBranch(final @NotNull String branchName, final @NotNull List<? extends GitRepository> repositories) {
    deleteRemoteBranches(Collections.singletonList(branchName), repositories);
  }

  public void deleteRemoteBranches(@NotNull List<String> branchNames, @NotNull List<? extends GitRepository> repositories) {
    updateInfo(repositories);
    new GitDeleteRemoteBranchOperation(myProject, myGit, myUiHandler, repositories, branchNames).execute();
  }

  public void merge(final @NotNull String branchName, final @NotNull GitBrancher.DeleteOnMergeOption deleteOnMerge,
                    final @NotNull List<? extends GitRepository> repositories) {
    updateInfo(repositories);
    new GitMergeOperation(myProject, myGit, myUiHandler, repositories, branchName, deleteOnMerge).execute();
  }

  public void rebase(@NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
    updateInfo(repositories);
    GitRebaseUtils.rebase(myProject, repositories, new GitRebaseParams(myVcs.getVersion(), branchName), myUiHandler.getProgressIndicator());
  }

  public void rebaseOnCurrent(@NotNull List<? extends GitRepository> repositories, @NotNull String branchName) {
    rebase(repositories, "HEAD", branchName); //NON-NLS
  }

  public void rebase(@NotNull List<? extends GitRepository> repositories, @NotNull String upstream, @NotNull String branchName) {
    updateInfo(repositories);
    GitRebaseUtils.rebase(myProject, repositories, new GitRebaseParams(myVcs.getVersion(), branchName, null, upstream, false, false),
                          myUiHandler.getProgressIndicator());
  }

  public void renameBranch(@NotNull String currentName, @NotNull String newName, @NotNull List<? extends GitRepository> repositories) {
    updateInfo(repositories);
    new GitRenameBranchOperation(myProject, myGit, myUiHandler, currentName, newName, repositories).execute();
  }

  public static @NotNull Collection<Change> loadTotalDiff(@NotNull Repository repository, @NotNull String branchName) throws VcsException {
    // return git diff between current working directory and branchName: working dir should be displayed as a 'left' one (base)
    return GitChangeUtils.getDiffWithWorkingDir(repository.getProject(), repository.getRoot(), branchName, null, true);
  }

  private static void updateInfo(@NotNull Collection<? extends GitRepository> repositories) {
    for (GitRepository repository : repositories) {
      repository.update();
    }
  }
}
