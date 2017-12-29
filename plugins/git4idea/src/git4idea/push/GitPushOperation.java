/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.PushSpec;
import com.intellij.dvcs.repo.Repository;
import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import git4idea.DialogManager;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitRevisionNumber;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandlerListener;
import git4idea.commands.GitStandardProgressAnalyzer;
import git4idea.config.GitVcsSettings;
import git4idea.config.UpdateMethod;
import git4idea.history.GitHistoryUtils;
import git4idea.merge.MergeChangeCollector;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.GitRebaseOverMergeProblem;
import git4idea.update.GitUpdateProcess;
import git4idea.update.GitUpdateResult;
import git4idea.update.GitUpdater;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.filter;
import static git4idea.push.GitPushNativeResult.Type.FORCED_UPDATE;
import static git4idea.push.GitPushNativeResult.Type.NEW_REF;
import static git4idea.push.GitPushRepoResult.Type.NOT_PUSHED;
import static git4idea.push.GitPushRepoResult.Type.REJECTED_NO_FF;

/**
 * Executes git push operation:
 * <ul>
 *   <li>Calls push for the given repositories with given parameters;</li>
 *   <li>Collects results;</li>
 *   <li>If push is rejected, proposes to update via merge or rebase;</li>
 *   <li>Shows a notification about push result</li>
 * </ul>
 */
public class GitPushOperation {

  private static final Logger LOG = Logger.getInstance(GitPushOperation.class);
  private static final int MAX_PUSH_ATTEMPTS = 10;

  private final Project myProject;
  @NotNull private final GitPushSupport myPushSupport;
  private final Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> myPushSpecs;
  @Nullable private final GitPushTagMode myTagMode;
  private final boolean myForce;
  private final boolean mySkipHook;
  private final Git myGit;
  private final ProgressIndicator myProgressIndicator;
  private final GitVcsSettings mySettings;
  private final GitRepositoryManager myRepositoryManager;

  public GitPushOperation(@NotNull Project project,
                          @NotNull GitPushSupport pushSupport,
                          @NotNull Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> pushSpecs,
                          @Nullable GitPushTagMode tagMode,
                          boolean force,
                          boolean skipHook) {
    myProject = project;
    myPushSupport = pushSupport;
    myPushSpecs = pushSpecs;
    myTagMode = tagMode;
    myForce = force;
    mySkipHook = skipHook;
    myGit = Git.getInstance();
    myProgressIndicator = ObjectUtils.notNull(ProgressManager.getInstance().getProgressIndicator(), new EmptyProgressIndicator());
    mySettings = GitVcsSettings.getInstance(myProject);
    myRepositoryManager = GitRepositoryManager.getInstance(myProject);

    Map<GitRepository, GitRevisionNumber> currentHeads = ContainerUtil.newHashMap();
    for (GitRepository repository : pushSpecs.keySet()) {
      repository.update();
      String head = repository.getCurrentRevision();
      if (head == null) {
        LOG.error("This repository has no commits");
      }
      else {
        currentHeads.put(repository, new GitRevisionNumber(head));
      }
    }
  }

  @NotNull
  public GitPushResult execute() {
    PushUpdateSettings updateSettings = readPushUpdateSettings();
    Label beforePushLabel = null;
    Label afterPushLabel = null;
    Map<GitRepository, String> preUpdatePositions = updateRootInfoAndRememberPositions();
    Boolean rebaseOverMergeProblemDetected = null;

    final Map<GitRepository, GitPushRepoResult> results = ContainerUtil.newHashMap();
    Map<GitRepository, GitUpdateResult> updatedRoots = ContainerUtil.newHashMap();

    try {
      Collection<GitRepository> remainingRoots = myPushSpecs.keySet();
      for (int pushAttempt = 0;
           pushAttempt < MAX_PUSH_ATTEMPTS && !remainingRoots.isEmpty();
           pushAttempt++, remainingRoots = getRejectedAndNotPushed(results)) {
        Map<GitRepository, GitPushRepoResult> resultMap = push(myRepositoryManager.sortByDependency(remainingRoots));
        results.putAll(resultMap);

        GroupedPushResult result = GroupedPushResult.group(resultMap);

        // stop if error happens, or if push is rejected for a custom reason (not because a pull is needed)
        if (!result.errors.isEmpty() || !result.customRejected.isEmpty()) {
          break;
        }

        // propose to update if rejected
        if (!result.rejected.isEmpty()) {
          boolean shouldUpdate = true;
          if (myForce || pushingToNotTrackedBranch(result.rejected)) {
            shouldUpdate = false;
          }
          else if (pushAttempt == 0 && !mySettings.autoUpdateIfPushRejected()) {
            // the dialog will be shown => check for rebase-over-merge problem in advance to avoid showing several dialogs in a row
            rebaseOverMergeProblemDetected = !findRootsWithMergeCommits(getRootsToUpdate(updateSettings,
                                                                                         result.rejected.keySet())).isEmpty();

            updateSettings = showDialogAndGetExitCode(result.rejected.keySet(), updateSettings,
                                                      rebaseOverMergeProblemDetected.booleanValue());
            if (updateSettings != null) {
              savePushUpdateSettings(updateSettings, rebaseOverMergeProblemDetected.booleanValue());
            }
            else {
              shouldUpdate = false;
            }
          }

          if (!shouldUpdate) {
            break;
          }

          if (beforePushLabel == null) { // put the label only before the very first update
            beforePushLabel = LocalHistory.getInstance().putSystemLabel(myProject, "Before push");
          }
          Collection<GitRepository> rootsToUpdate = getRootsToUpdate(updateSettings, result.rejected.keySet());
          GitUpdateResult updateResult = update(rootsToUpdate, updateSettings.getUpdateMethod(), rebaseOverMergeProblemDetected == null);
          for (GitRepository repository : rootsToUpdate) {
            updatedRoots.put(repository, updateResult); // TODO update result in GitUpdateProcess is a single for several roots
          }
          if (!updateResult.isSuccess() ||
              updateResult == GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS || updateResult == GitUpdateResult.INCOMPLETE) {
            break;
          }
        }
      }
    }
    finally {
      if (beforePushLabel != null) {
        afterPushLabel = LocalHistory.getInstance().putSystemLabel(myProject, "After push");
      }
      for (GitRepository repository : myPushSpecs.keySet()) {
        repository.update();
      }
    }
    return prepareCombinedResult(results, updatedRoots, preUpdatePositions, beforePushLabel, afterPushLabel);
  }

  @NotNull
  private Collection<GitRepository> getRootsToUpdate(@NotNull PushUpdateSettings updateSettings,
                                                     @NotNull Set<GitRepository> rejectedRepositories) {
    return updateSettings.shouldUpdateAllRoots() ? myRepositoryManager.getRepositories() : rejectedRepositories;
  }

  @NotNull
  private Collection<VirtualFile> findRootsWithMergeCommits(@NotNull Collection<GitRepository> rootsToSearch) {
    return ContainerUtil.mapNotNull(rootsToSearch, repo -> {
      PushSpec<GitPushSource, GitPushTarget> pushSpec = myPushSpecs.get(repo);
      if (pushSpec == null) { // repository is not selected to be pushed, but can be rebased
        GitPushSource source = myPushSupport.getSource(repo);
        GitPushTarget target = myPushSupport.getDefaultTarget(repo);
        if (target == null) {
          return null;
        }
        pushSpec = new PushSpec<>(source, target);
      }
      String baseRef = pushSpec.getTarget().getBranch().getFullName();
      String currentRef = pushSpec.getSource().getBranch().getFullName();
      return GitRebaseOverMergeProblem.hasProblem(myProject, repo.getRoot(), baseRef, currentRef) ? repo.getRoot() : null;
    });
  }

  private static boolean pushingToNotTrackedBranch(@NotNull Map<GitRepository, GitPushRepoResult> rejected) {
    return ContainerUtil.exists(rejected.entrySet(), entry -> {
      GitRepository repository = entry.getKey();
      GitLocalBranch currentBranch = repository.getCurrentBranch();
      assert currentBranch != null;
      GitBranchTrackInfo trackInfo = GitBranchUtil.getTrackInfoForBranch(repository, currentBranch);
      return trackInfo == null || !trackInfo.getRemoteBranch().getFullName().equals(entry.getValue().getTargetBranch());
    });
  }

  @NotNull
  private static List<GitRepository> getRejectedAndNotPushed(@NotNull final Map<GitRepository, GitPushRepoResult> results) {
    return filter(results.keySet(),
                                repository -> results.get(repository).getType() == REJECTED_NO_FF ||
                                              results.get(repository).getType() == NOT_PUSHED);
  }

  @NotNull
  private Map<GitRepository, String> updateRootInfoAndRememberPositions() {
    Set<GitRepository> repositories = myPushSpecs.keySet();
    repositories.forEach(GitRepository::update);
    return StreamEx.of(repositories).toMap(Repository::getCurrentRevision);
  }

  @NotNull
  private GitPushResult prepareCombinedResult(@NotNull Map<GitRepository, GitPushRepoResult> allRoots,
                                              @NotNull Map<GitRepository, GitUpdateResult> updatedRoots,
                                              @NotNull Map<GitRepository, String> preUpdatePositions,
                                              @Nullable Label beforeUpdateLabel,
                                              @Nullable Label afterUpdateLabel) {
    Map<GitRepository, GitPushRepoResult> results = ContainerUtil.newHashMap();
    UpdatedFiles updatedFiles = UpdatedFiles.create();
    for (Map.Entry<GitRepository, GitPushRepoResult> entry : allRoots.entrySet()) {
      GitRepository repository = entry.getKey();
      GitPushRepoResult simpleResult = entry.getValue();
      GitUpdateResult updateResult = updatedRoots.get(repository);
      if (updateResult == null) {
        results.put(repository, simpleResult);
      }
      else {
        collectUpdatedFiles(updatedFiles, repository, preUpdatePositions.get(repository));
        results.put(repository, GitPushRepoResult.addUpdateResult(simpleResult, updateResult));
      }
    }
    return new GitPushResult(results, updatedFiles, beforeUpdateLabel, afterUpdateLabel);
  }

  @NotNull
  private Map<GitRepository, GitPushRepoResult> push(@NotNull List<GitRepository> repositories) {
    Map<GitRepository, GitPushRepoResult> results = ContainerUtil.newLinkedHashMap();
    for (GitRepository repository : repositories) {
      PushSpec<GitPushSource, GitPushTarget> spec = myPushSpecs.get(repository);
      ResultWithOutput resultWithOutput = doPush(repository, spec);
      LOG.debug("Pushed to " + DvcsUtil.getShortRepositoryName(repository) + ": " + resultWithOutput);

      GitLocalBranch source = spec.getSource().getBranch();
      GitPushTarget target = spec.getTarget();
      GitPushRepoResult repoResult;
      if (resultWithOutput.isError()) {
        repoResult = GitPushRepoResult.error(source, target.getBranch(), resultWithOutput.getErrorAsString());
      }
      else {
        List<GitPushNativeResult> nativeResults = resultWithOutput.parsedResults;
        final GitPushNativeResult branchResult = getBranchResult(nativeResults);
        if (branchResult == null) {
          LOG.error("No result for branch among: [" + nativeResults + "]\n" +
                    "Full result: " + resultWithOutput);
          continue;
        }
        List<GitPushNativeResult> tagResults = filter(nativeResults, result ->
          !result.equals(branchResult) && (result.getType() == NEW_REF || result.getType() == FORCED_UPDATE));
        int commits = collectNumberOfPushedCommits(repository.getRoot(), branchResult);
        repoResult = GitPushRepoResult.convertFromNative(branchResult, tagResults, commits, source, target.getBranch());
      }

      LOG.debug("Converted result: " + repoResult);
      results.put(repository, repoResult);
    }

    // fill other not-processed repositories as not-pushed
    for (GitRepository repository : repositories) {
      if (!results.containsKey(repository)) {
        PushSpec<GitPushSource, GitPushTarget> spec = myPushSpecs.get(repository);
        results.put(repository, GitPushRepoResult.notPushed(spec.getSource().getBranch(), spec.getTarget().getBranch()));
      }
    }
    return results;
  }

  @Nullable
  private static GitPushNativeResult getBranchResult(@NotNull List<GitPushNativeResult> results) {
    return ContainerUtil.find(results, result -> result.getSourceRef().startsWith("refs/heads/"));
  }

  private int collectNumberOfPushedCommits(@NotNull VirtualFile root, @NotNull GitPushNativeResult result) {
    if (result.getType() != GitPushNativeResult.Type.SUCCESS) {
      return -1;
    }
    String range = result.getRange();
    if (range == null) {
      LOG.error("Range of pushed commits not reported in " + result);
      return -1;
    }
    try {
      return GitHistoryUtils.history(myProject, root, range).size();
    }
    catch (VcsException e) {
      LOG.error("Couldn't collect commits from range " + range);
      return -1;
    }
  }

  private void collectUpdatedFiles(@NotNull UpdatedFiles updatedFiles, @NotNull GitRepository repository,
                                   @NotNull String preUpdatePosition) {
    MergeChangeCollector collector = new MergeChangeCollector(myProject, repository.getRoot(), new GitRevisionNumber(preUpdatePosition));
    ArrayList<VcsException> exceptions = new ArrayList<>();
    collector.collect(updatedFiles, exceptions);
    for (VcsException exception : exceptions) {
      LOG.info(exception);
    }
  }

  @NotNull
  private ResultWithOutput doPush(@NotNull GitRepository repository, @NotNull PushSpec<GitPushSource, GitPushTarget> pushSpec) {
    GitPushTarget target = pushSpec.getTarget();
    GitLocalBranch sourceBranch = pushSpec.getSource().getBranch();
    GitRemoteBranch targetBranch = target.getBranch();

    GitLineHandlerListener progressListener = GitStandardProgressAnalyzer.createListener(myProgressIndicator);
    boolean setUpstream = pushSpec.getTarget().isNewBranchCreated() && !branchTrackingInfoIsSet(repository, sourceBranch);
    String tagMode = myTagMode == null ? null : myTagMode.getArgument();

    String spec = sourceBranch.getFullName() + ":" + targetBranch.getNameForRemoteOperations();
    GitCommandResult res =
      myGit.push(repository, targetBranch.getRemote(), spec, myForce, setUpstream, mySkipHook, tagMode, progressListener);
    return new ResultWithOutput(res);
  }

  private static boolean branchTrackingInfoIsSet(@NotNull GitRepository repository, @NotNull final GitLocalBranch source) {
    return ContainerUtil.exists(repository.getBranchTrackInfos(), info -> info.getLocalBranch().equals(source));
  }

  private void savePushUpdateSettings(@NotNull PushUpdateSettings settings, boolean rebaseOverMergeDetected) {
    UpdateMethod updateMethod = settings.getUpdateMethod();
    mySettings.setUpdateAllRootsIfPushRejected(settings.shouldUpdateAllRoots());
    if (!rebaseOverMergeDetected // don't overwrite explicit "rebase" with temporary "merge" caused by merge commits
        && mySettings.getUpdateType() != updateMethod && mySettings.getUpdateType() != UpdateMethod.BRANCH_DEFAULT) { // don't overwrite "branch default" setting
      mySettings.setUpdateType(updateMethod);
    }
  }

  @NotNull
  private PushUpdateSettings readPushUpdateSettings() {
    boolean updateAllRoots = mySettings.shouldUpdateAllRootsIfPushRejected();
    UpdateMethod updateMethod = mySettings.getUpdateType();
    if (updateMethod == UpdateMethod.BRANCH_DEFAULT) {
      // deliberate limitation: we have only 2 buttons => choose method from the 1st repo if different
      updateMethod = GitUpdater.resolveUpdateMethod(myPushSpecs.keySet().iterator().next());
    }
    return new PushUpdateSettings(updateAllRoots, updateMethod);
  }

  @Nullable
  private PushUpdateSettings showDialogAndGetExitCode(@NotNull final Set<GitRepository> repositories,
                                                      @NotNull final PushUpdateSettings initialSettings,
                                                      final boolean rebaseOverMergeProblemDetected) {
    Ref<PushUpdateSettings> updateSettings = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      GitRejectedPushUpdateDialog dialog = new GitRejectedPushUpdateDialog(myProject, repositories, initialSettings,
                                                                           rebaseOverMergeProblemDetected);
      DialogManager.show(dialog);
      int exitCode = dialog.getExitCode();
      if (exitCode != DialogWrapper.CANCEL_EXIT_CODE) {
        mySettings.setAutoUpdateIfPushRejected(dialog.shouldAutoUpdateInFuture());
        updateSettings.set(new PushUpdateSettings(dialog.shouldUpdateAll(), convertUpdateMethodFromDialogExitCode(exitCode)));
      }
    });
    return updateSettings.get();
  }

  @NotNull
  private static UpdateMethod convertUpdateMethodFromDialogExitCode(int exitCode) {
    switch (exitCode) {
      case GitRejectedPushUpdateDialog.MERGE_EXIT_CODE:  return UpdateMethod.MERGE;
      case GitRejectedPushUpdateDialog.REBASE_EXIT_CODE: return UpdateMethod.REBASE;
      default: throw new IllegalStateException("Unexpected exit code: " + exitCode);
    }
  }

  @NotNull
  protected GitUpdateResult update(@NotNull Collection<GitRepository> rootsToUpdate,
                                   @NotNull UpdateMethod updateMethod,
                                   boolean checkForRebaseOverMergeProblem) {
    GitUpdateResult updateResult = new GitUpdateProcess(myProject, myProgressIndicator,
                                                        new HashSet<>(rootsToUpdate), UpdatedFiles.create(),
                                                        checkForRebaseOverMergeProblem, false).update(updateMethod);
    for (GitRepository repository : rootsToUpdate) {
      repository.getRoot().refresh(true, true);
      repository.update();
    }
    return updateResult;
  }

  private static class ResultWithOutput {
    @NotNull private final List<GitPushNativeResult> parsedResults;
    @NotNull private final GitCommandResult resultOutput;

    ResultWithOutput(@NotNull GitCommandResult resultOutput) {
      this.resultOutput = resultOutput;
      this.parsedResults = GitPushNativeResultParser.parse(resultOutput.getOutput());
    }

    boolean isError() {
      return parsedResults.isEmpty();
    }

    @NotNull
    String getErrorAsString() {
      return resultOutput.getErrorOutputAsJoinedString();
    }

    @Override
    public String toString() {
      return "Parsed results: " + parsedResults + "\nCommand output:" + resultOutput;
    }
  }
}
