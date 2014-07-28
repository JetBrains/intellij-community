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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitCommit;
import git4idea.GitPlatformFacade;
import git4idea.commands.*;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

  GitDeleteBranchOperation(@NotNull Project project, GitPlatformFacade facade, @NotNull Git git, @NotNull GitBranchUiHandler uiHandler,
                           @NotNull Collection<GitRepository> repositories, @NotNull String branchName) {
    super(project, facade, git, uiHandler, repositories);
    myBranchName = branchName;
  }

  @Override
  public void execute() {
    boolean fatalErrorHappened = false;
    while (hasMoreRepositories() && !fatalErrorHappened) {
      final GitRepository repository = next();

      GitSimpleEventDetector notFullyMergedDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.BRANCH_NOT_FULLY_MERGED);
      GitBranchNotMergedToUpstreamDetector notMergedToUpstreamDetector = new GitBranchNotMergedToUpstreamDetector();
      GitCommandResult result = myGit.branchDelete(repository, myBranchName, false, notFullyMergedDetector, notMergedToUpstreamDetector);

      if (result.success()) {
        refresh(repository);
        markSuccessful(repository);
      }
      else if (notFullyMergedDetector.hasHappened()) {
        String baseBranch = notMergedToUpstreamDetector.getBaseBranch();
        if (baseBranch == null) { // GitBranchNotMergedToUpstreamDetector didn't happen
          baseBranch = myCurrentHeads.get(repository);
        }

        Collection<GitRepository> remainingRepositories = getRemainingRepositories();
        boolean forceDelete = showNotFullyMergedDialog(myBranchName, baseBranch, remainingRepositories);
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
          if (wereSuccessful())  {
            showFatalErrorDialogWithRollback(getErrorTitle(), "This branch is not fully merged to " + baseBranch + ".");
          }
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
      repository.update();
    }
  }

  @Override
  protected void rollback() {
    GitCompoundResult result = new GitCompoundResult(myProject);
    for (GitRepository repository : getSuccessfulRepositories()) {
      GitCommandResult res = myGit.branchCreate(repository, myBranchName);
      result.append(repository, res);
      refresh(repository);
    }

    if (!result.totalSuccess()) {
      VcsNotifier.getInstance(myProject).notifyError("Error during rollback of branch deletion",
                                                     result.getErrorOutputWithReposIndication());
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
      GitCommandResult res = myGit.branchDelete(repository, branchName, true);
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
  private boolean showNotFullyMergedDialog(@NotNull final String unmergedBranch, @NotNull final String baseBranch,
                                           @NotNull Collection<GitRepository> repositories) {
    final List<String> mergedToBranches = getMergedToBranches(unmergedBranch);

    final Map<GitRepository, List<GitCommit>> history = new HashMap<GitRepository, List<GitCommit>>();

    // note getRepositories() instead of getRemainingRepositories() here:
    // we don't confuse user with the absence of repositories that have succeeded, just show no commits for them (and don't query for log)
    for (GitRepository repository : getRepositories()) {
      if (repositories.contains(repository)) {
        history.put(repository, getUnmergedCommits(repository, unmergedBranch, baseBranch));
      }
      else {
        history.put(repository, Collections.<GitCommit>emptyList());
      }
    }

    return myUiHandler.showBranchIsNotFullyMergedDialog(myProject, history, unmergedBranch, mergedToBranches, baseBranch);
  }

  @NotNull
  private List<GitCommit> getUnmergedCommits(@NotNull GitRepository repository, @NotNull String branchName, @NotNull String baseBranch) {
    return myGit.history(repository, baseBranch + ".." + branchName);
  }

  @NotNull
  private List<String> getMergedToBranches(String branchName) {
    List<String> mergedToBranches = null;
    for (GitRepository repository : getRemainingRepositories()) {
      List<String> branches = getMergedToBranches(repository, branchName);
      if (mergedToBranches == null) {
        mergedToBranches = branches;
      } 
      else {
        mergedToBranches = new ArrayList<String>(ContainerUtil.intersection(mergedToBranches, branches));
      }
    }
    return mergedToBranches != null ? mergedToBranches : new ArrayList<String>();
  }

  /**
   * Branches which the given branch is merged to ({@code git branch --merged},
   * except the given branch itself.
   */
  @NotNull
  private List<String> getMergedToBranches(@NotNull GitRepository repository, @NotNull String branchName) {
    String tip = tip(repository, branchName);
    if (tip == null) {
      return Collections.emptyList();
    }
    return branchContainsCommit(repository, tip, branchName);
  }

  @Nullable
  private String tip(GitRepository repository, @NotNull String branchName) {
    GitCommandResult result = myGit.tip(repository, branchName);
    if (result.success() && result.getOutput().size() == 1) {
      return result.getOutput().get(0).trim();
    }
    // failing in this method is not critical - it is just additional information. So we just log the error
    LOG.info("Failed to get [git rev-list -1] for branch [" + branchName + "]. " + result);
    return null;
  }

  @NotNull
  private List<String> branchContainsCommit(@NotNull GitRepository repository, @NotNull String tip, @NotNull String branchName) {
    GitCommandResult result = myGit.branchContains(repository, tip);
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

  // warning: not deleting branch 'feature' that is not yet merged to
  //          'refs/remotes/origin/feature', even though it is merged to HEAD.
  // error: The branch 'feature' is not fully merged.
  // If you are sure you want to delete it, run 'git branch -D feature'.
  private static class GitBranchNotMergedToUpstreamDetector implements GitLineHandlerListener {

    private static final Pattern PATTERN = Pattern.compile(".*'(.*)', even though it is merged to.*");
    @Nullable private String myBaseBranch;

    @Override
    public void onLineAvailable(String line, Key outputType) {
      Matcher matcher = PATTERN.matcher(line);
      if (matcher.matches()) {
        myBaseBranch = matcher.group(1);
      }
    }

    @Override
    public void processTerminated(int exitCode) {
    }

    @Override
    public void startFailed(Throwable exception) {
    }

    @Nullable
    public String getBaseBranch() {
      return myBaseBranch;
    }
  }
}
