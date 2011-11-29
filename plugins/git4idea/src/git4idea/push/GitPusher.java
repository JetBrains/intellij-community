/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.push;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.Git;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchPair;
import git4idea.commands.GitCommandResult;
import git4idea.config.GitVcsSettings;
import git4idea.config.UpdateMethod;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.jgit.GitHttpAdapter;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.settings.GitPushSettings;
import git4idea.update.GitUpdateProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects information to push and performs the push.
 *
 * @author Kirill Likhodedov
 */
public final class GitPusher {

  /**
   * if diff-log is not available (new branch is created, for example), we show a few recent commits made on the branch
   */
  static final int RECENT_COMMITS_NUMBER = 5;
  
  static final GitBranch NO_TARGET_BRANCH = new GitBranch("", false, true);

  private static final Logger LOG = Logger.getInstance(GitPusher.class);
  private static final String INDICATOR_TEXT = "Pushing";

  private final Project myProject;
  private final ProgressIndicator myProgressIndicator;
  private final Collection<GitRepository> myRepositories;
  private final GitVcsSettings mySettings;
  private final GitPushSettings myPushSettings;

  public static void showPushDialogAndPerformPush(@NotNull final Project project) {
    final GitPushDialog dialog = new GitPushDialog(project);
    dialog.show();
    if (dialog.isOK()) {
      Task.Backgroundable task = new Task.Backgroundable(project, INDICATOR_TEXT, false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          new GitPusher(project, indicator).push(dialog.getPushInfo());
        }
      };
      GitVcs.runInBackground(task);
    }
  }

  // holds settings chosen in GitRejectedPushUpdate dialog to reuse if the next push is rejected again.
  static class UpdateSettings {
    private final boolean myUpdateAllRoots;
    private final UpdateMethod myUpdateMethod;

    private UpdateSettings(boolean updateAllRoots, UpdateMethod updateMethod) {
      myUpdateAllRoots = updateAllRoots;
      myUpdateMethod = updateMethod;
    }

    public boolean shouldUpdateAllRoots() {
      return myUpdateAllRoots;
    }

    public UpdateMethod getUpdateMethod() {
      return myUpdateMethod;
    }

    public boolean shouldUpdate() {
      return getUpdateMethod() != null;
    }
  }

  public GitPusher(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    myProject = project;
    myProgressIndicator = indicator;
    myRepositories = GitRepositoryManager.getInstance(project).getRepositories();
    mySettings = GitVcsSettings.getInstance(myProject);
    myPushSettings = GitPushSettings.getInstance(myProject);
  }

  /**
   *
   *
   *
   * @param pushSpec which branches in which repositories should be pushed.
   *                               The most common situation is all repositories in the project with a single currently active branch for
   *                               each of them.
   * @throws VcsException if couldn't query 'git log' about commits to be pushed.
   * @return
   */
  @NotNull
  GitCommitsByRepoAndBranch collectCommitsToPush(@NotNull Map<GitRepository, GitPushSpec> pushSpecs) throws VcsException {
    Map<GitRepository, List<GitBranchPair>> reposAndBranchesToPush = prepareRepositoriesAndBranchesToPush(pushSpecs);
    
    Map<GitRepository, GitCommitsByBranch> commitsByRepoAndBranch = new HashMap<GitRepository, GitCommitsByBranch>();
    for (GitRepository repository : myRepositories) {
      List<GitBranchPair> branchPairs = reposAndBranchesToPush.get(repository);
      if (branchPairs == null) {
        continue;
      }
      GitCommitsByBranch commitsByBranch = collectsCommitsToPush(repository, branchPairs);
      commitsByRepoAndBranch.put(repository, commitsByBranch);
    }
    return new GitCommitsByRepoAndBranch(commitsByRepoAndBranch);
  }

  @NotNull
  private Map<GitRepository, List<GitBranchPair>> prepareRepositoriesAndBranchesToPush(@NotNull Map<GitRepository, GitPushSpec> pushSpecs) throws VcsException {
    Map<GitRepository, List<GitBranchPair>> res = new HashMap<GitRepository, List<GitBranchPair>>();
    for (GitRepository repository : myRepositories) {
      GitPushSpec pushSpec = pushSpecs.get(repository);
      if (pushSpec == null) {
        continue;
      }
      if (!pushSpec.isPushAll()) {
        res.put(repository, Collections.singletonList(new GitBranchPair(pushSpec.getSource(), pushSpec.getDest())));
      } else {
        res.put(repository, GitPushSpec.getBranchesForPushAll(repository));
      }
    }
    return res;
  }

  @NotNull
  private GitCommitsByBranch collectsCommitsToPush(@NotNull GitRepository repository, @NotNull List<GitBranchPair> sourcesDestinations)
    throws VcsException {
    Map<GitBranch, GitPushBranchInfo> commitsByBranch = new HashMap<GitBranch, GitPushBranchInfo>();

    for (GitBranchPair sourceDest : sourcesDestinations) {
      GitBranch source = sourceDest.getBranch();
      GitBranch dest = sourceDest.getDest();
      assert dest != null : "Destination branch can't be null here for branch " + source;

      List<GitCommit> commits;
      GitPushBranchInfo.Type type;
      if (dest == NO_TARGET_BRANCH) {
        commits = collectRecentCommitsOnBranch(repository, source);
        type = GitPushBranchInfo.Type.NO_TRACKED_OR_TARGET;
      }
      else if (GitUtil.repoContainsRemoteBranch(repository, dest)) {
        commits = collectCommitsToPush(repository, source.getName(), dest.getName());
        type = GitPushBranchInfo.Type.STANDARD;
      } 
      else {
        commits = collectRecentCommitsOnBranch(repository, source);
        type = GitPushBranchInfo.Type.NEW_BRANCH;
      }
      commitsByBranch.put(source, new GitPushBranchInfo(source, dest, commits, type));
    }

    return new GitCommitsByBranch(commitsByBranch);
  }

  private List<GitCommit> collectRecentCommitsOnBranch(GitRepository repository, GitBranch source) throws VcsException {
    return GitHistoryUtils.history(myProject, repository.getRoot(), "--max-count=" + RECENT_COMMITS_NUMBER, source.getName());
  }

  @NotNull
  private List<GitCommit> collectCommitsToPush(@NotNull GitRepository repository, @NotNull String source, @NotNull String destination)
    throws VcsException {
    return GitHistoryUtils.history(myProject, repository.getRoot(), destination + ".." + source);
  }

  /**
   * Makes push, shows the result in a notification. If push for current branch is rejected, shows a dialog proposing to update.
   */
  public void push(@NotNull GitPushInfo pushInfo) {
    push(pushInfo, null, null);
  }

  /**
   * Makes push, shows the result in a notification. If push for current branch is rejected, shows a dialog proposing to update.
   * If {@code previousResult} and {@code updateSettings} are set, it means that this push is not the first, but is after a successful update.
   * In that case, if push is rejected again, the dialog is not shown, and update is performed automatically with the previously chosen
   * option.
   * Also, at the end results are merged and are shown in a single notification.
   */
  private void push(@NotNull GitPushInfo pushInfo, @Nullable GitPushResult previousResult, @Nullable UpdateSettings updateSettings) {
    GitPushResult result = tryPushAndGetResult(pushInfo);
    handleResult(pushInfo, result, previousResult, updateSettings);
  }

  @NotNull
  private GitPushResult tryPushAndGetResult(@NotNull GitPushInfo pushInfo) {
    GitPushResult pushResult = new GitPushResult(myProject);
    
    GitCommitsByRepoAndBranch commits = pushInfo.getCommits();
    for (GitRepository repository : commits.getRepositories()) {
      if (commits.get(repository).getAllCommits().size() == 0) {  // don't push repositories where there is nothing to push. Note that when a branch is created, several recent commits are stored in the pushInfo.
        continue;
      }
      GitPushRepoResult repoResult = pushRepository(pushInfo, commits, repository);
      if (repoResult.getType() == GitPushRepoResult.Type.NOT_PUSHING) {
        continue;
      }
      pushResult.append(repository, repoResult);
      GitPushRepoResult.Type resultType = repoResult.getType();
      if (resultType == GitPushRepoResult.Type.CANCEL || resultType == GitPushRepoResult.Type.NOT_AUTHORIZED) { // don't proceed if user has cancelled or couldn't login
        break;
      }
    }
    GitRepositoryManager.getInstance(myProject).updateAllRepositories(GitRepository.TrackedTopic.BRANCHES); // new remote branch may be created
    return pushResult;
  }

  @NotNull
  private static GitPushRepoResult pushRepository(@NotNull GitPushInfo pushInfo,
                                                  @NotNull GitCommitsByRepoAndBranch commits,
                                                  @NotNull GitRepository repository) {
    GitPushSpec pushSpec = pushInfo.getPushSpecs().get(repository);
    GitSimplePushResult simplePushResult = pushAndGetSimpleResult(repository, pushSpec, commits.get(repository));
    String output = simplePushResult.getOutput();
    switch (simplePushResult.getType()) {
      case SUCCESS:
        return successOrErrorRepoResult(commits, repository, output, true);
      case ERROR:
        return successOrErrorRepoResult(commits, repository, output, false);
      case REJECT:
        return getResultFromRejectedPush(commits, repository, simplePushResult);
      case NOT_AUTHORIZED:
        return GitPushRepoResult.notAuthorized(output);
      case CANCEL:
        return GitPushRepoResult.cancelled(output);
      case NOT_PUSHED:
        return GitPushRepoResult.notPushed();
      default:
        return GitPushRepoResult.cancelled(output);
    }
  }

  @NotNull
  private static GitPushRepoResult getResultFromRejectedPush(@NotNull GitCommitsByRepoAndBranch commits,
                                                             @NotNull GitRepository repository,
                                                             @NotNull GitSimplePushResult simplePushResult) {
    Collection<String> rejectedBranches = simplePushResult.getRejectedBranches();

    Map<GitBranch, GitPushBranchResult> resultMap = new HashMap<GitBranch, GitPushBranchResult>();
    GitCommitsByBranch commitsByBranch = commits.get(repository);
    boolean pushedBranchWasRejected = false;
    for (GitBranch branch : commitsByBranch.getBranches()) {
      GitPushBranchResult branchResult;
      if (branchInRejected(branch, rejectedBranches)) {
        branchResult = GitPushBranchResult.rejected();
        pushedBranchWasRejected = true;
      }
      else {
        branchResult = successfulResultForBranch(commitsByBranch, branch);
      }
      resultMap.put(branch, branchResult);
    }

    if (pushedBranchWasRejected) {
      return GitPushRepoResult.someRejected(resultMap, simplePushResult.getOutput());
    } else {
      // The rejectedDetector detected rejected push of the branch which had nothing to push (but is behind the upstream). We are not counting it.
      return GitPushRepoResult.success(resultMap, simplePushResult.getOutput());
    }
  }

  @NotNull
  private static GitSimplePushResult pushAndGetSimpleResult(GitRepository repository, GitPushSpec pushSpec, GitCommitsByBranch commitsByBranch) {
    if (pushSpec.getDest() == NO_TARGET_BRANCH) {
      return GitSimplePushResult.notPushed();
    }

    if (pushSpec.isPushAll()) {
      // TODO support pushing to different branches with http remotes from one and ssh from other.
      // Currently it is a hack - get just one remote url and hope that others are the same type and the same server.
      String remoteUrl = null;
      for (GitBranch branch : commitsByBranch.getBranches()) {
        if (remoteUrl != null) {
          break;
        }
        try {
          String remoteName = branch.getTrackedRemoteName(repository.getProject(), repository.getRoot());
          GitRemote remote = GitUtil.findRemoteByName(repository, remoteName);
          if (remote != null) {
            if (!remote.getPushUrls().isEmpty()) {
              remoteUrl = remote.getPushUrls().iterator().next();
            }
          }
        }
        catch (VcsException e) {
          LOG.info(e);
        }
      }

      if (remoteUrl == null) {
        return pushNatively(repository, pushSpec);
      }
      else {
        return GitHttpAdapter.isHttpUrl(remoteUrl) ? GitHttpAdapter.push(repository, null, remoteUrl) : pushNatively(repository, pushSpec);
      }
    }
    else {
      GitRemote remote = pushSpec.getRemote();
      assert remote != null : "Remote can't be null for pushSpec " + pushSpec;
      String httpUrl = null;
      for (String pushUrl : remote.getPushUrls()) {
        if (GitHttpAdapter.isHttpUrl(pushUrl)) {
          httpUrl = pushUrl;
          break;            // TODO support http and ssh urls in one origin
        }
      }
      if (httpUrl != null) {
        return GitHttpAdapter.push(repository, remote, httpUrl);
      }
      else {
        return pushNatively(repository, pushSpec);
      }
    }
  }

  @NotNull
  private static GitSimplePushResult pushNatively(GitRepository repository, GitPushSpec pushSpec) {
    GitPushRejectedDetector rejectedDetector = new GitPushRejectedDetector();
    GitCommandResult res = Git.push(repository, pushSpec, rejectedDetector);
    if (rejectedDetector.rejected()) {
      Collection<String> rejectedBranches = rejectedDetector.getRejectedBranches();
      return GitSimplePushResult.reject(rejectedBranches);
    }
    else if (res.success()) {
      return GitSimplePushResult.success();
    }
    else {
      return GitSimplePushResult.error(res.getErrorOutputAsHtmlString());
    }
  }

  @NotNull
  private static GitPushRepoResult successOrErrorRepoResult(@NotNull GitCommitsByRepoAndBranch commits, @NotNull GitRepository repository, @NotNull String output, boolean success) {
    GitPushRepoResult repoResult;
    Map<GitBranch, GitPushBranchResult> resultMap = new HashMap<GitBranch, GitPushBranchResult>();
    GitCommitsByBranch commitsByBranch = commits.get(repository);
    for (GitBranch branch : commitsByBranch.getBranches()) {
      GitPushBranchResult branchResult = success ?
                                         successfulResultForBranch(commitsByBranch, branch) :
                                         GitPushBranchResult.error();
      resultMap.put(branch, branchResult);
    }
    repoResult = success ? GitPushRepoResult.success(resultMap, output) : GitPushRepoResult.error(resultMap,  output);
    return repoResult;
  }

  @NotNull
  private static GitPushBranchResult successfulResultForBranch(@NotNull GitCommitsByBranch commitsByBranch, @NotNull GitBranch branch) {
    GitPushBranchInfo branchInfo = commitsByBranch.get(branch);
    if (branchInfo.isNewBranchCreated()) {
      return GitPushBranchResult.newBranch(branchInfo.getDestBranch().getName());
    } 
    return GitPushBranchResult.success(branchInfo.getCommits().size());
  }

  private static boolean branchInRejected(@NotNull GitBranch branch, @NotNull Collection<String> rejectedBranches) {
    String branchName = branch.getName();
    final String REFS_HEADS = "refs/heads/";
    if (branchName.startsWith(REFS_HEADS)) {
      branchName = branchName.substring(REFS_HEADS.length());
    }
    
    for (String rejectedBranch : rejectedBranches) {
      if (rejectedBranch.equals(branchName) || (rejectedBranch.startsWith(REFS_HEADS) &&  rejectedBranch.substring(REFS_HEADS.length()).equals(branchName))) {
        return true;
      }
    }
    return false;
  }

  // if all repos succeeded, show notification.
  // if all repos failed, show notification.
  // if some repos failed, show notification with both results.
  // if in failed repos, some branches were rejected, it is a warning-type, but not an error.
  // if in a failed repo, current branch was rejected, propose to update the branch. Don't do it if not current branch was rejected,
  // since it is difficult to update such a branch.
  // if in a failed repo, a branch was rejected that had nothing to push, don't notify about the rejection.
  // Besides all of the above, don't confuse users with 1 repository with all this "repository/root" stuff;
  // don't confuse users which push only a single branch with all this "branch" stuff.
  private void handleResult(@NotNull GitPushInfo pushInfo, @NotNull GitPushResult result, @Nullable GitPushResult previousResult, @Nullable UpdateSettings updateSettings) {
    result.mergeFrom(previousResult);

    if (result.isEmpty()) {
      GitVcs.NOTIFICATION_GROUP_ID.createNotification("Nothing to push", NotificationType.INFORMATION).notify(myProject);
    }
    else if (result.wasErrorCancelOrNotAuthorized()) {
      // if there was an error on any repo, we won't propose to update even if current branch of a repo was rejected
      result.createNotification().notify(myProject);
    }
    else {
      // there were no errors, but there might be some rejected branches on some of the repositories
      // => for current branch propose to update and re-push it. For others just warn
      Map<GitRepository, GitBranch> rejectedPushesForCurrentBranch = result.getRejectedPushesForCurrentBranch();

      if (!rejectedPushesForCurrentBranch.isEmpty()) {

        if (updateSettings == null) {
          // show dialog only when push is rejected for the first time in a row, otherwise reuse previously chosen update method
          // and don't show the dialog again if user has chosen not to ask again
          updateSettings = readUpdateSettings();
          if (!mySettings.autoUpdateIfPushRejected()) {
            final GitRejectedPushUpdateDialog dialog = new GitRejectedPushUpdateDialog(myProject, rejectedPushesForCurrentBranch.keySet(), updateSettings);
            final int exitCode = showDialogAndGetExitCode(dialog);
            updateSettings = new UpdateSettings(dialog.shouldUpdateAll(), getUpdateMethodFromDialogExitCode(exitCode));
            saveUpdateSettings(updateSettings);
          }
        } 

        if (updateSettings.shouldUpdate()) {
          Collection<GitRepository> repositoriesToUpdate = getRootsToUpdate(rejectedPushesForCurrentBranch, updateSettings.shouldUpdateAllRoots());
          GitPushResult adjustedPushResult = result.remove(rejectedPushesForCurrentBranch);
          adjustedPushResult.markUpdateStartIfNotMarked(repositoriesToUpdate);
          boolean updateResult = update(getRootsFromRepositories(repositoriesToUpdate), updateSettings.getUpdateMethod());
          if (updateResult) {
            myProgressIndicator.setText(INDICATOR_TEXT);
            GitPushInfo newPushInfo = pushInfo.retain(rejectedPushesForCurrentBranch);
            push(newPushInfo, adjustedPushResult, updateSettings);
            return; // don't notify - next push will notify all results in compound
          }
        }

      }

      result.createNotification().notify(myProject);
    }
  }

  private void saveUpdateSettings(@NotNull UpdateSettings updateSettings) {
    myPushSettings.setUpdateAllRoots(updateSettings.shouldUpdateAllRoots());
    myPushSettings.setUpdateMethod(updateSettings.getUpdateMethod());
  }

  @NotNull
  private UpdateSettings readUpdateSettings() {
    boolean updateAllRoots = myPushSettings.shouldUpdateAllRoots();
    UpdateMethod updateMethod = myPushSettings.getUpdateMethod();
    return new UpdateSettings(updateAllRoots, updateMethod);
  }

  private int showDialogAndGetExitCode(@NotNull final GitRejectedPushUpdateDialog dialog) {
    final AtomicInteger exitCode = new AtomicInteger();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        dialog.show();
        exitCode.set(dialog.getExitCode());
      }
    });
    int code = exitCode.get();
    if (code != DialogWrapper.CANCEL_EXIT_CODE) {
      mySettings.setAutoUpdateIfPushRejected(dialog.shouldAutoUpdateInFuture());
    }
    return code;
  }

  /**
   * @return update method selected in the dialog or {@code null} if user pressed Cancel, i.e. doesn't want to update.
   */
  @Nullable
  private static UpdateMethod getUpdateMethodFromDialogExitCode(int exitCode) {
    switch (exitCode) {
      case GitRejectedPushUpdateDialog.MERGE_EXIT_CODE:  return UpdateMethod.MERGE;
      case GitRejectedPushUpdateDialog.REBASE_EXIT_CODE: return UpdateMethod.REBASE;
    }
    return null;
  }

  @NotNull
  private Collection<GitRepository> getRootsToUpdate(@NotNull Map<GitRepository, GitBranch> rejectedPushesForCurrentBranch, boolean updateAllRoots) {
    return updateAllRoots ? myRepositories : rejectedPushesForCurrentBranch.keySet();
  }
  
  @NotNull
  private static Collection<VirtualFile> getRootsFromRepositories(@NotNull Collection<GitRepository> repositories) {
    Collection<VirtualFile> roots = new ArrayList<VirtualFile>();
    for (GitRepository repository : repositories) {
      roots.add(repository.getRoot());
    }
    return roots;
  }

  private boolean update(@NotNull Collection<VirtualFile> rootsToUpdate, @NotNull UpdateMethod updateMethod) {
    GitUpdateProcess.UpdateMethod um = updateMethod == UpdateMethod.MERGE ? GitUpdateProcess.UpdateMethod.MERGE : GitUpdateProcess.UpdateMethod.REBASE;
    boolean updateResult = new GitUpdateProcess(myProject, myProgressIndicator, new HashSet<VirtualFile>(rootsToUpdate), UpdatedFiles.create()).update(um);
    for (VirtualFile virtualFile : rootsToUpdate) {
      virtualFile.refresh(true, true);
    }
    return updateResult;
  }

}
