// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.CompareBranchesDialog;
import com.intellij.dvcs.util.CommitCompareInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitCommit;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.i18n.GitBundle;
import git4idea.history.GitHistoryUtils;
import git4idea.rebase.GitRebaseUtils;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitCompareBranchesHelper;
import git4idea.util.GitLocalCommitCompareInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;

/**
 * Executes the logic of git branch operations.
 * All operations are run in the current thread.
 * All UI interaction is done via the {@link GitBranchUiHandler} passed to the constructor.
 */
public final class GitBranchWorker {

  private static final Logger LOG = Logger.getInstance(GitBranchWorker.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final GitBranchUiHandler myUiHandler;
  @NotNull private final GitVcs myVcs;

  public GitBranchWorker(@NotNull Project project, @NotNull Git git, @NotNull GitBranchUiHandler uiHandler) {
    myProject = project;
    myGit = git;
    myUiHandler = uiHandler;
    myVcs = GitVcs.getInstance(myProject);
  }
  
  public void checkoutNewBranch(@NotNull final String name, @NotNull List<? extends GitRepository> repositories) {
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
        VcsNotifier.getInstance(myProject).notifyError(error, result.getErrorOutputAsHtmlString(), true);
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

  public void checkout(@NotNull final String reference, boolean detach, @NotNull List<? extends GitRepository> repositories) {
    updateInfo(repositories);
    new GitCheckoutOperation(myProject, myGit, myUiHandler, repositories, reference, detach, false, false, null).execute();
  }


  public void deleteBranch(@NotNull final String branchName, @NotNull final List<? extends GitRepository> repositories) {
    updateInfo(repositories);
    new GitDeleteBranchOperation(myProject, myGit, myUiHandler, repositories, branchName).execute();
  }

  public void deleteTag(@NotNull final String tagName, @NotNull final List<? extends GitRepository> repositories) {
    updateInfo(repositories);
    new GitDeleteTagOperation(myProject, myGit, myUiHandler, repositories, tagName).execute();
  }

  public void deleteRemoteTag(@NotNull final String tagName, @NotNull final Map<GitRepository, String> repositories) {
    updateInfo(repositories.keySet());
    new GitDeleteRemoteTagOperation(myProject, myGit, myUiHandler, repositories, tagName).execute();
  }

  public void deleteRemoteBranch(@NotNull final String branchName, @NotNull final List<? extends GitRepository> repositories) {
    deleteRemoteBranches(Collections.singletonList(branchName), repositories);
  }

  public void deleteRemoteBranches(@NotNull List<String> branchNames, @NotNull List<? extends GitRepository> repositories) {
    updateInfo(repositories);
    new GitDeleteRemoteBranchOperation(myProject, myGit, myUiHandler, repositories, branchNames).execute();
  }

  public void merge(@NotNull final String branchName, @NotNull final GitBrancher.DeleteOnMergeOption deleteOnMerge,
                    @NotNull final List<? extends GitRepository> repositories) {
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

  void compare(@NotNull String branchName, @NotNull List<? extends GitRepository> repositories, @NotNull GitRepository selectedRepository) {
    try {
      CommitCompareInfo myCompareInfo = loadCommitsToCompare(repositories, branchName);
      ApplicationManager.getApplication().invokeLater(
        () -> displayCompareDialog(branchName, GitBranchUtil.getCurrentBranchOrRev(repositories), myCompareInfo, selectedRepository));
    }
    catch (VcsException e) {
      VcsNotifier.getInstance(myProject).notifyError(GitBundle.message("compare.branches.error"), e.getMessage());
    }
  }

  @NotNull
  private CommitCompareInfo loadCommitsToCompare(@NotNull List<? extends GitRepository> repositories,
                                                 @NotNull String branchName) throws VcsException {
    CommitCompareInfo compareInfo = new GitLocalCommitCompareInfo(myProject, branchName);
    for (GitRepository repository : repositories) {
      List<GitCommit> headToBranch = GitHistoryUtils.history(myProject, repository.getRoot(), ".." + branchName);
      List<GitCommit> branchToHead = GitHistoryUtils.history(myProject, repository.getRoot(), branchName + "..");
      compareInfo.put(repository, headToBranch, branchToHead);

      compareInfo.putTotalDiff(repository, loadTotalDiff(repository, branchName));
    }
    return compareInfo;
  }

  @NotNull
  public static Collection<Change> loadTotalDiff(@NotNull Repository repository, @NotNull String branchName) throws VcsException {
    // return git diff between current working directory and branchName: working dir should be displayed as a 'left' one (base)
    return GitChangeUtils.getDiffWithWorkingDir(repository.getProject(), repository.getRoot(), branchName, null, true);
  }

  private void displayCompareDialog(@NotNull String branchName, @NotNull String currentBranch, @NotNull CommitCompareInfo compareInfo,
                                    @NotNull GitRepository selectedRepository) {
    if (compareInfo.isEmpty()) {
      Messages.showInfoMessage(myProject, GitBundle.message("compare.branches.no.changes.message.description", currentBranch, branchName),
                               GitBundle.message("compare.branches.no.changes.message.title"));
    }
    else {
      new CompareBranchesDialog(new GitCompareBranchesHelper(myProject),
                                branchName, currentBranch, compareInfo, selectedRepository, false).show();
    }
  }

  private static void updateInfo(@NotNull Collection<? extends GitRepository> repositories) {
    for (GitRepository repository : repositories) {
      repository.update();
    }
  }
}
