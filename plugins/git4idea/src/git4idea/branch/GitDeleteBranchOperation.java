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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.GitExecutionException;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.repo.GitRepository;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deletes a branch.
 * If branch is not fully merged to the current branch, shows a dialog with the list of unmerged commits and with a list of branches
 * current branch are merged to, and makes force delete, if wanted.
 *
 * @author Kirill Likhodedov
 */
class GitDeleteBranchOperation extends GitBranchOperation {
  
  private static final Logger LOG = Logger.getInstance(GitDeleteBranchOperation.class);

  private final String myBranchName;
  private final String myCurrentBranch;

  GitDeleteBranchOperation(@NotNull Project project, @NotNull Collection<GitRepository> repositories,
                           @NotNull String branchName, @NotNull String currentBranch, @NotNull ProgressIndicator indicator) {
    super(project, repositories, currentBranch, indicator);
    myBranchName = branchName;
    myCurrentBranch = currentBranch;
  }

  @Override
  public void execute() {
    boolean fatalErrorHappened = false;
    while (hasMoreRepositories() && !fatalErrorHappened) {
      final GitRepository repository = next();

      GitSimpleEventDetector notFullyMergedDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.BRANCH_NOT_FULLY_MERGED);
      GitCommandResult result = Git.branchDelete(repository, myBranchName, false, notFullyMergedDetector);

      if (result.success()) {
        refresh(repository);
        markSuccessful(repository);
      }
      else if (notFullyMergedDetector.hasHappened()) {
        Collection<GitRepository> remainingRepositories = getRemainingRepositories();
        boolean forceDelete = showNotFullyMergedDialog(myBranchName, remainingRepositories);
        if (forceDelete) {
          GitCompoundResult compoundResult = forceDelete(myBranchName, remainingRepositories);
          if (compoundResult.totalSuccess()) {
            GitRepository[] remainingRepositoriesArray = ArrayUtil.toObjectArray(remainingRepositories, GitRepository.class);
            markSuccessful(remainingRepositoriesArray);
            refresh(remainingRepositoriesArray);
          }
          else {
            fatalError(getErrorTitle(), compoundResult.getErrorOutputWithReposIndication());
            return;
          }
        }
        else {
          fatalError(getErrorTitle(), "This branch is not fully merged to " + myCurrentBranch + ".");
          fatalErrorHappened = true;
        }
      }
      else {
        fatalError(getErrorTitle(), result.getErrorOutputAsJoinedString());
        fatalErrorHappened = true;
      }
    }

    if (!fatalErrorHappened) {
      notifySuccess();
    }
  }

  private static void refresh(@NotNull GitRepository... repositories) {
    for (GitRepository repository : repositories) {
      repository.update(GitRepository.TrackedTopic.BRANCHES, GitRepository.TrackedTopic.CONFIG);
    }
  }

  @Override
  protected void rollback() {
    GitCompoundResult result = new GitCompoundResult(myProject);
    for (GitRepository repository : getSuccessfulRepositories()) {
      GitCommandResult res = Git.branchCreate(repository, myBranchName);
      result.append(repository, res);
      refresh(repository);
    }

    if (!result.totalSuccess()) {
      GitUIUtil.notify(GitVcs.IMPORTANT_ERROR_NOTIFICATION, myProject, "Error during rollback of branch deletion",
                       result.getErrorOutputWithReposIndication(), NotificationType.ERROR, null);
    }
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
  @Override
  protected String getRollbackProposal() {
    return "However branch deletion has succeeded for the following " + repositories() + ":<br/>" +
           successfulRepositoriesJoined() +
           "<br/>You may rollback (recreate " + myBranchName + " in these roots) not to let branches diverge.";
  }

  @NotNull
  @Override
  protected String getOperationName() {
    return "branch deletion";
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
    for (GitRepository repository : getRepositories()) {
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
    for (GitRepository repository : getRepositories()) {
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

}
