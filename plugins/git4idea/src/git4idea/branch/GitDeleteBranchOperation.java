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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ui.UIUtil;
import git4idea.commands.Git;
import git4idea.GitExecutionException;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Note: GitDeleteBranchOperation doesn't implement {@link GitBranchOperation}, because we don't need nor retry, neither rollback,
 * but we need a lot of additional customizations.
 * 
 * @author Kirill Likhodedov
 */
class GitDeleteBranchOperation {
  
  private static final Logger LOG = Logger.getInstance(GitDeleteBranchOperation.class);

  private final String myBranchName;
  private final String myCurrentBranch;
  private final ProgressIndicator myIndicator;
  private final Project myProject;
  private final Collection<GitRepository> myRepositories;

  public GitDeleteBranchOperation(@NotNull Project project, @NotNull Collection<GitRepository> repositories, @NotNull String branchName, @NotNull String currentBranch, @NotNull ProgressIndicator indicator) {
    myProject = project;
    myRepositories = repositories;
    myBranchName = branchName;
    myCurrentBranch = currentBranch;
    myIndicator = indicator;
  }

  public void execute() {
    Collection<GitRepository> succeeded = new ArrayList<GitRepository>();
    for (GitRepository repository : myRepositories) {
      GitSimpleEventDetector notFullyMergedDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.BRANCH_NOT_FULLY_MERGED);
      GitCommandResult result = Git.branchDelete(repository, myBranchName, false, notFullyMergedDetector);
      if (result.success()) {
        succeeded.add(repository);
      }
      else if (notFullyMergedDetector.hasHappened()) {
        Collection<GitRepository> remainingRepos = filterOut(myRepositories, succeeded);
        boolean forceDelete = showNotFullyMergedDialog(myBranchName, remainingRepos);
        if (forceDelete) {
          GitCompoundResult compoundResult = forceDelete(myBranchName, remainingRepos);
          if (compoundResult.totalSuccess()) {
            break;
          }
          else {
            notifyError(succeeded, compoundResult);
            return;
          }
        }
        else {
          if (succeeded.isEmpty()) {
            GitMultiRootOperationExecutor
              .showFatalError(getErrorTitle(), "The branch is not fully merged to the current branch.", myProject);
          }
          else {
            StringBuilder message = new StringBuilder();
            message.append("Successfully removed in ").append(GitMultiRootOperationExecutor.joinRepositoryUrls(succeeded, "<br/>"))
              .append("The branch is not fully merged to the current branch in other repositories.");
            GitMultiRootOperationExecutor.showFatalError(getErrorTitle() + " in some repositories", message.toString(), myProject);
          }
          return;
        }
      }
      else {
        GitMultiRootOperationExecutor.showFatalError(getErrorTitle(), result.getErrorOutputAsHtmlString(), myProject);
        return;
      }
    }
    GitMultiRootOperationExecutor.notifySuccess(getSuccessMessage(), myProject);
  }

  private void notifyError(Collection<GitRepository> succeeded, GitCompoundResult compoundResult) {
    if (succeeded.isEmpty()) {
      GitMultiRootOperationExecutor.showFatalError(getErrorTitle(), compoundResult.getErrorOutputWithReposIndication(), myProject);
    }
    else {
      notifyPartialError(succeeded, compoundResult);
    }
  }

  private void notifyPartialError(Collection<GitRepository> succeeded, GitCompoundResult compoundResult) {
    String title = "Couldn't delete branch " + myBranchName + " in some repositories";
    StringBuilder message = new StringBuilder();
    message.append("Successfully removed in ").append(GitMultiRootOperationExecutor.joinRepositoryUrls(succeeded, "<br/>"))
      .append(compoundResult.getErrorOutputWithReposIndication());
    GitMultiRootOperationExecutor.showFatalError(title, message.toString(), myProject);
  }

  @NotNull
  private String getErrorTitle() {
    return String.format("Branch %s wasn't deleted", myBranchName);
  }

  @NotNull
  public String getSuccessMessage() {
    return String.format("Deleted branch <b><code>%s</code></b>", myBranchName);
  }

  @NotNull
  private GitCompoundResult forceDelete(@NotNull String branchName, @NotNull Collection<GitRepository> possibleFailedRepositories) {
    GitCompoundResult compoundResult = new GitCompoundResult(myProject);
    for (GitRepository repository : possibleFailedRepositories) {
      GitCommandResult res = Git.branchDelete(repository, branchName, true);
      compoundResult.append(repository, res);
    }
    return compoundResult;
  }

  /**
   * Shows a dialog "the branch is not fully merged" with the list of unmerged commits.
   * User may still want to force delete the branch.
   * In multi-repository setup collects unmerged commits for all given repositories.
   * @return true if the branch should be force deleted.
   */
  private boolean showNotFullyMergedDialog(@NotNull final String branchName, @NotNull Collection<GitRepository> repositories) {
    final List<String> mergedToBranches = getMergedToBranches(branchName);

    final Map<GitRepository, List<GitCommit>> history = new HashMap<GitRepository, List<GitCommit>>();
    for (GitRepository repository : myRepositories) {
      // we don't confuse user with the absence of repositories that have succeeded, just show no commits for them (and don't query for log)
      if (repositories.contains(repository)) {
        history.put(repository, getUnmergedCommits(repository, branchName));
      }
      else {
        history.put(repository, Collections.<GitCommit>emptyList());
      }
    }

    final AtomicBoolean forceDelete = new AtomicBoolean();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        forceDelete.set(GitBranchIsNotFullyMergedDialog.showAndGetAnswer(myProject, history, branchName, mergedToBranches, myCurrentBranch));
      }
    });
    return forceDelete.get();
  }

  @NotNull
  private List<GitCommit> getUnmergedCommits(@NotNull GitRepository repository, @NotNull String branchName) {
    List<GitCommit> history;
    try {
      history = GitHistoryUtils.history(myProject, repository.getRoot(), ".." + branchName);
    } catch (VcsException e) {
      // this is critical, because we need to show the list of unmerged commits, and it shouldn't happen => inform user and developer
      throw new GitExecutionException("Couldn't get [git log .." + branchName + "] on repository [" + repository.getRoot() + "]", e);
    }
    return history;
  }

  @NotNull
  private List<String> getMergedToBranches(String branchName) {
    List<String> mergedToBranches = null;
    for (GitRepository repository : myRepositories) {
      List<String> branches = getMergedToBranches(repository, branchName);
      if (mergedToBranches == null) {
        mergedToBranches = branches;
      } 
      else {
        mergedToBranches.retainAll(branches);
      }
    }
    return mergedToBranches != null ? mergedToBranches : new ArrayList<String>();
  }

  /**
   * Branches which the given branch is merged to ({@code git branch --merged},
   * except the given branch itself.
   */
  @NotNull
  private static List<String> getMergedToBranches(@NotNull GitRepository repository, @NotNull String branchName) {
    String tip = tip(repository, branchName);
    if (tip == null) {
      return Collections.emptyList();
    }
    return branchContainsCommit(repository, tip, branchName);
  }

  @Nullable
  private static String tip(GitRepository repository, @NotNull String branchName) {
    GitCommandResult result = Git.tip(repository, branchName);
    if (result.success() && result.getOutput().size() == 1) {
      return result.getOutput().get(0).trim();
    }
    // failing in this method is not critical - it is just additional information. So we just log the error
    LOG.info("Failed to get [git rev-list -1] for branch [" + branchName + "]. " + result);
    return null;
  }

  @NotNull
  private static List<String> branchContainsCommit(@NotNull GitRepository repository, @NotNull String tip, @NotNull String branchName) {
    GitCommandResult result = Git.branchContains(repository, tip);
    if (result.success()) {
      List<String> branches = new ArrayList<String>();
      for (String s : result.getOutput()) {
        s = s.trim();
        if (s.startsWith("*")) {
          s = s.substring(2);
        }
        if (!s.equals(branchName)) { // this branch contains itself - not interesting
          branches.add(s);
        }
      }
      return branches;
    }

    // failing in this method is not critical - it is just additional information. So we just log the error
    LOG.info("Failed to get [git branch --contains] for hash [" + tip + "]. " + result);
    return Collections.emptyList();
  }


  @NotNull
  public static <T> Collection<T> filterOut(@NotNull Collection<T> original, @NotNull Collection<T> toRemove) {
    Collection<T> filtered = new ArrayList<T>(original);
    filtered.removeAll(toRemove);
    return filtered;
  }
}
