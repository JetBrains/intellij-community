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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitExecutionException;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitBranchUiUtil;
import git4idea.ui.branch.GitCompareBranchesDialog;
import git4idea.ui.branch.GitMultiRootBranchConfig;
import git4idea.util.GitCommitCompareInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Executor of Git branching operations.
 *
 * @author Kirill Likhodedov
 */
public final class GitBranchOperationsProcessor {

  private static final Logger LOG = Logger.getInstance(GitBranchOperationsProcessor.class);

  private final Project myProject;
  private final Collection<GitRepository> myRepositories;
  @Nullable private final Runnable myCallInAwtAfterExecution;
  private final GitRepository mySelectedRepository;

  public GitBranchOperationsProcessor(@NotNull GitRepository repository) {
    this(repository, null);
  }
  
  public GitBranchOperationsProcessor(@NotNull GitRepository repository, @Nullable Runnable callInAwtAfterExecution) {
    this(repository.getProject(), Collections.singleton(repository), repository, callInAwtAfterExecution);
  }

  public GitBranchOperationsProcessor(@NotNull Project project, @NotNull Collection<GitRepository> repositories,
                                      @NotNull GitRepository selectedRepository) {
    this(project, repositories, selectedRepository, null);
  }

  public GitBranchOperationsProcessor(@NotNull Project project,
                                      @NotNull Collection<GitRepository> repositories,
                                      @NotNull GitRepository selectedRepository,
                                      @Nullable Runnable callInAwtAfterExecution) {
    myProject = project;
    myRepositories = repositories;
    mySelectedRepository = selectedRepository;
    myCallInAwtAfterExecution = callInAwtAfterExecution;
  }
  
  @NotNull
  private String getCurrentBranch() {
    if (myRepositories.size() > 1) {
      GitMultiRootBranchConfig multiRootBranchConfig = new GitMultiRootBranchConfig(myRepositories);
      String currentBranch = multiRootBranchConfig.getCurrentBranch();
      LOG.assertTrue(currentBranch != null, "Repositories have unexpectedly diverged. " + multiRootBranchConfig);
      return currentBranch;
    }
    else {
      assert !myRepositories.isEmpty() : "No repositories passed to GitBranchOperationsProcessor.";
      GitRepository repository = myRepositories.iterator().next();
      return GitBranchUiUtil.getBranchNameOrRev(repository);
    }
  }

  /**
   * Checks out a new branch in background.
   * If there are unmerged files, proposes to resolve the conflicts and tries to check out again.
   * Doesn't check the name of new branch for validity - do this before calling this method, otherwise a standard error dialog will be shown.
   *
   * @param name Name of the new branch to check out.
   */
  public void checkoutNewBranch(@NotNull final String name) {
    new CommonBackgroundTask(myProject, "Checking out new branch " + name, myCallInAwtAfterExecution) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        doCheckoutNewBranch(name, indicator);
      }
    }.runInBackground();
  }

  public void createNewTag(@NotNull final String name, final String reference) {
    new CommonBackgroundTask(myProject, "Checking out new branch " + name, myCallInAwtAfterExecution) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        for (GitRepository repository : myRepositories) {
          Git.createNewTag(repository, name, null, reference);
        }
      }
    }.runInBackground();
  }

  private void doCheckoutNewBranch(@NotNull final String name, @NotNull ProgressIndicator indicator) {
    new GitCheckoutNewBranchOperation(myProject, myRepositories, name, getCurrentBranch(), indicator).execute();
  }


  @NotNull
  static GitConflictResolver prepareConflictResolverForUnmergedFilesBeforeCheckout(Project project, Collection<VirtualFile> roots) {
    GitConflictResolver.Params params = new GitConflictResolver.Params().
      setMergeDescription("The following files have unresolved conflicts. You need to resolve them before checking out.").
      setErrorNotificationTitle("Can't create new branch");
    return new GitConflictResolver(project, roots, params);
  }

  /**
   * Creates and checks out a new local branch starting from the given reference:
   * {@code git checkout -b <branchname> <start-point>}. <br/>
   * If the reference is a remote branch, and the tracking is wanted, pass {@code true} in the "track" parameter.
   * Provides the "smart checkout" procedure the same as in {@link #checkout(String)}.
   *
   * @param newBranchName     Name of new local branch.
   * @param startPoint        Reference to checkout.
   */
  public void checkoutNewBranchStartingFrom(@NotNull String newBranchName, @NotNull String startPoint) {
    commonCheckout(startPoint, newBranchName);
  }

  /**
   * <p>
   *   Checks out the given reference (a branch, or a reference name, or a commit hash).
   *   If local changes prevent the checkout, shows the list of them and proposes to make a "smart checkout":
   *   stash-checkout-unstash.
   * </p>
   * <p>
   *   Doesn't check the reference for validity.
   * </p>
   *
   * @param reference reference to be checked out.
   */
  public void checkout(@NotNull final String reference) {
    commonCheckout(reference, null);
  }

  private void commonCheckout(@NotNull final String reference, @Nullable final String newBranch) {
    new CommonBackgroundTask(myProject, "Checking out " + reference, myCallInAwtAfterExecution) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        doCheckout(indicator, reference, newBranch);
      }
    }.runInBackground();
  }

  private void doCheckout(@NotNull ProgressIndicator indicator, @NotNull String reference, @Nullable String newBranch) {
    new GitCheckoutOperation(myProject, myRepositories, reference, newBranch, getCurrentBranch(), indicator).execute();
  }

  public void deleteBranch(final String branchName) {
    new CommonBackgroundTask(myProject, "Deleting " + branchName, myCallInAwtAfterExecution) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        doDelete(branchName, indicator);
      }
    }.runInBackground();
  }

  private void doDelete(final String branchName, ProgressIndicator indicator) {
    GitDeleteBranchOperation operation = new GitDeleteBranchOperation(myProject, myRepositories, branchName, getCurrentBranch(), indicator);
    operation.execute();
    for (GitRepository repository : myRepositories) {
      repository.update(GitRepository.TrackedTopic.BRANCHES, GitRepository.TrackedTopic.CONFIG);
    }
  }

  /**
   * Compares the HEAD with the specified branch - shows a dialog with the differences.
   * @param branchName name of the branch to compare with.
   */
  public void compare(@NotNull final String branchName) {
    new CommonBackgroundTask(myProject, "Comparing with " + branchName, myCallInAwtAfterExecution) {
  
      private GitCommitCompareInfo myCompareInfo;
  
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        myCompareInfo = loadCommitsToCompare(myRepositories, branchName);
      }
  
      @Override
      public void onSuccess() {
        if (myCompareInfo == null) {
          LOG.error("The task to get compare info didn't finish. Repositories: \n" + myRepositories + "\nbranch name: " + branchName);
          return;
        }
        displayCompareDialog(branchName, getCurrentBranch(), myCompareInfo);
      }
    }.runInBackground();
  }

  private GitCommitCompareInfo loadCommitsToCompare(Collection<GitRepository> repositories, String branchName) {
    GitCommitCompareInfo compareInfo = new GitCommitCompareInfo();
    for (GitRepository repository : repositories) {
      compareInfo.put(repository, loadCommitsToCompare(repository, branchName));
    }
    return compareInfo;
  }
  
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
  
  private void displayCompareDialog(@NotNull String branchName, @NotNull String currentBranch, @NotNull GitCommitCompareInfo compareInfo) {
    if (compareInfo.isEmpty()) {
      Messages.showInfoMessage(myProject, String.format("<html>There are no changes between <code>%s</code> and <code>%s</code></html>",
                                                        currentBranch, branchName), "No Changes Detected");
    }
    else {
      new GitCompareBranchesDialog(myProject, branchName, currentBranch, compareInfo, mySelectedRepository).show();
    }
  }

  /**
   * Executes common operations before/after executing the actual branch operation.
   */
  private static abstract class CommonBackgroundTask extends Task.Backgroundable {

    @Nullable private final Runnable myCallInAwtAfterExecution;

    private CommonBackgroundTask(@Nullable final Project project, @NotNull final String title, @Nullable Runnable callInAwtAfterExecution) {
      super(project, title);
      myCallInAwtAfterExecution = callInAwtAfterExecution;
    }

    @Override
    public final void run(@NotNull ProgressIndicator indicator) {
      saveAllDocuments();
      execute(indicator);
      if (myCallInAwtAfterExecution != null) {
        SwingUtilities.invokeLater(myCallInAwtAfterExecution);
      }
    }

    abstract void execute(@NotNull ProgressIndicator indicator);

    void runInBackground() {
      GitVcs.runInBackground(this);
    }

    private static void saveAllDocuments() {
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          FileDocumentManager.getInstance().saveAllDocuments();
        }
      });
    }
  }

}
