/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.branch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import git4idea.GitCommit;
import git4idea.GitExecutionException;
import git4idea.GitPlatformFacade;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitCompareBranchesDialog;
import git4idea.util.GitCommitCompareInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes the logic of git branch operations.
 * All operations are run in the current thread.
 * All UI interaction is done via the {@link GitBranchUiHandler} passed to the constructor.
 *
 * @author Kirill Likhodedov
 */
public final class GitBranchWorker {

  private static final Logger LOG = Logger.getInstance(GitBranchWorker.class);

  @NotNull private final Project myProject;
  @NotNull private final GitPlatformFacade myFacade;
  @NotNull private final Git myGit;
  @NotNull private final GitBranchUiHandler myUiHandler;

  public GitBranchWorker(@NotNull Project project, @NotNull GitPlatformFacade facade, @NotNull Git git, @NotNull GitBranchUiHandler uiHandler) {
    myProject = project;
    myFacade = facade;
    myGit = git;
    myUiHandler = uiHandler;
  }
  
  public void checkoutNewBranch(@NotNull final String name, @NotNull final List<GitRepository> repositories) {
    updateInfo(repositories);
    new GitCheckoutNewBranchOperation(myProject, myFacade, myGit, myUiHandler, repositories, name).execute();
  }

  public void createNewTag(@NotNull final String name, @NotNull final String reference, @NotNull final List<GitRepository> repositories) {
    updateInfo(repositories);
    for (GitRepository repository : repositories) {
      myGit.createNewTag(repository, name, null, reference);
    }
  }

  public void checkoutNewBranchStartingFrom(@NotNull String newBranchName, @NotNull String startPoint,
                                            @NotNull List<GitRepository> repositories) {
    updateInfo(repositories);
    new GitCheckoutOperation(myProject, myFacade, myGit, myUiHandler, repositories, startPoint, newBranchName).execute();
  }

  public void checkout(@NotNull final String reference, @NotNull List<GitRepository> repositories) {
    updateInfo(repositories);
    new GitCheckoutOperation(myProject, myFacade, myGit, myUiHandler, repositories, reference, null).execute();
  }


  public void deleteBranch(@NotNull final String branchName, @NotNull final List<GitRepository> repositories) {
    updateInfo(repositories);
    new GitDeleteBranchOperation(myProject, myFacade, myGit, myUiHandler, repositories, branchName).execute();
  }

  public void deleteRemoteBranch(@NotNull final String branchName, @NotNull final List<GitRepository> repositories) {
    updateInfo(repositories);
    new GitDeleteRemoteBranchOperation(myProject, myFacade, myGit, myUiHandler, repositories, branchName).execute();
  }

  public void merge(@NotNull final String branchName, @NotNull final GitBrancher.DeleteOnMergeOption deleteOnMerge,
                    @NotNull final List<GitRepository> repositories) {
    updateInfo(repositories);
    Map<GitRepository, String> revisions = new HashMap<GitRepository, String>();
    for (GitRepository repository : repositories) {
      revisions.put(repository, repository.getCurrentRevision());
    }
    new GitMergeOperation(myProject, myFacade, myGit, myUiHandler, repositories, branchName, deleteOnMerge, revisions).execute();
  }

  public void compare(@NotNull final String branchName, @NotNull final List<GitRepository> repositories,
                      @NotNull final GitRepository selectedRepository) {
    final GitCommitCompareInfo myCompareInfo = loadCommitsToCompare(repositories, branchName);
    if (myCompareInfo == null) {
      LOG.error("The task to get compare info didn't finish. Repositories: \n" + repositories + "\nbranch name: " + branchName);
      return;
    }
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        displayCompareDialog(branchName, GitBranchUtil.getCurrentBranchOrRev(repositories), myCompareInfo, selectedRepository);
      }
    });
  }

  private GitCommitCompareInfo loadCommitsToCompare(List<GitRepository> repositories, String branchName) {
    GitCommitCompareInfo compareInfo = new GitCommitCompareInfo();
    for (GitRepository repository : repositories) {
      compareInfo.put(repository, loadCommitsToCompare(repository, branchName));
      compareInfo.put(repository, loadTotalDiff(repository, branchName));
    }
    return compareInfo;
  }

  @NotNull
  private static Collection<Change> loadTotalDiff(@NotNull GitRepository repository, @NotNull String branchName) {
    try {
      return GitChangeUtils.getDiff(repository.getProject(), repository.getRoot(), null, branchName, null);
    }
    catch (VcsException e) {
      // we treat it as critical and report an error
      throw new GitExecutionException("Couldn't get [git diff " + branchName + "] on repository [" + repository.getRoot() + "]", e);
    }
  }

  @NotNull
  private Pair<List<GitCommit>, List<GitCommit>> loadCommitsToCompare(@NotNull GitRepository repository, @NotNull final String branchName) {
    final List<GitCommit> headToBranch;
    final List<GitCommit> branchToHead;
    try {
      headToBranch = GitHistoryUtils.history(myProject, repository.getRoot(), ".." + branchName);
      branchToHead = GitHistoryUtils.history(myProject, repository.getRoot(), branchName + "..");
    }
    catch (VcsException e) {
      // we treat it as critical and report an error
      throw new GitExecutionException("Couldn't get [git log .." + branchName + "] on repository [" + repository.getRoot() + "]", e);
    }
    return Pair.create(headToBranch, branchToHead);
  }
  
  private void displayCompareDialog(@NotNull String branchName, @NotNull String currentBranch, @NotNull GitCommitCompareInfo compareInfo,
                                    @NotNull GitRepository selectedRepository) {
    if (compareInfo.isEmpty()) {
      Messages.showInfoMessage(myProject, String.format("<html>There are no changes between <code>%s</code> and <code>%s</code></html>",
                                                        currentBranch, branchName), "No Changes Detected");
    }
    else {
      new GitCompareBranchesDialog(myProject, branchName, currentBranch, compareInfo, selectedRepository).show();
    }
  }

  private static void updateInfo(@NotNull Collection<GitRepository> repositories) {
    for (GitRepository repository : repositories) {
      repository.update();
    }
  }

}
