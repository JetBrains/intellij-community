// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.PushSpec;
import com.intellij.dvcs.repo.Repository;
import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
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
import git4idea.i18n.GitBundle;
import git4idea.merge.MergeChangeCollector;
import git4idea.push.GitPushParamsImpl.ForceWithLeaseReference;
import git4idea.push.GitRejectedPushUpdateDialog.Companion.PushRejectedExitCode;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.vcs.log.util.VcsLogUtil.HASH_REGEX;
import static com.intellij.vcs.log.util.VcsLogUtil.HEAD;
import static git4idea.commands.GitAuthenticationListener.GIT_AUTHENTICATION_SUCCESS;
import static git4idea.push.GitPushNativeResult.Type.FORCED_UPDATE;
import static git4idea.push.GitPushNativeResult.Type.NEW_REF;
import static git4idea.push.GitPushRepoResult.Type.NOT_PUSHED;
import static git4idea.push.GitPushRepoResult.Type.REJECTED_NO_FF;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

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
  private final ForceMode myForceMode;
  private final boolean mySkipHook;
  private final Git myGit;
  private final ProgressIndicator myProgressIndicator;
  private final GitVcsSettings mySettings;
  private final GitRepositoryManager myRepositoryManager;
  @NotNull private final Map<GitRepository, HashRange> myUpdatedRanges = new LinkedHashMap<>();

  public GitPushOperation(@NotNull Project project,
                          @NotNull GitPushSupport pushSupport,
                          @NotNull Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> pushSpecs,
                          @Nullable GitPushTagMode tagMode,
                          boolean force,
                          boolean skipHook) {
    this(project, pushSupport, pushSpecs, tagMode, getForceMode(force), skipHook);
  }

  @NotNull
  private static ForceMode getForceMode(boolean force) {
    if (force) return AdvancedSettings.getBoolean("git.use.push.force.with.lease") ? ForceMode.FORCE_WITH_LEASE : ForceMode.FORCE;
    return ForceMode.NONE;
  }

  public GitPushOperation(@NotNull Project project,
                          @NotNull GitPushSupport pushSupport,
                          @NotNull Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> pushSpecs,
                          @Nullable GitPushTagMode tagMode,
                          @NotNull ForceMode forceMode,
                          boolean skipHook) {
    myProject = project;
    myPushSupport = pushSupport;
    myPushSpecs = pushSpecs;
    myTagMode = tagMode;
    myForceMode = forceMode;
    mySkipHook = skipHook;
    myGit = Git.getInstance();
    myProgressIndicator = ObjectUtils.notNull(ProgressManager.getInstance().getProgressIndicator(), new EmptyProgressIndicator());
    mySettings = GitVcsSettings.getInstance(myProject);
    myRepositoryManager = GitRepositoryManager.getInstance(myProject);
  }

  @NotNull
  public GitPushResult execute() {
    PushUpdateSettings updateSettings = readPushUpdateSettings();
    Label beforePushLabel = null;
    Label afterPushLabel = null;
    Map<GitRepository, String> preUpdatePositions = updateRootInfoAndRememberPositions();
    Boolean rebaseOverMergeProblemDetected = null;

    final Map<GitRepository, GitPushRepoResult> results = new HashMap<>();
    Map<GitRepository, GitUpdateResult> updatedRoots = new HashMap<>();

    try {
      Collection<GitRepository> remainingRoots = myPushSpecs.keySet();
      for (int pushAttempt = 0;
           pushAttempt < MAX_PUSH_ATTEMPTS && !remainingRoots.isEmpty();
           pushAttempt++, remainingRoots = getRejectedAndNotPushed(results)) {
        LOG.debug("Starting push attempt #" + pushAttempt);
        Map<GitRepository, GitPushRepoResult> resultMap = push(myRepositoryManager.sortByDependency(remainingRoots));
        results.putAll(resultMap);

        GroupedPushResult result = GroupedPushResult.group(resultMap);

        // stop if error happens, or if push is rejected for a custom reason (not because a pull is needed)
        if (!result.errors.isEmpty() || !result.customRejected.isEmpty()) {
          break;
        }

        if (!result.rejected.isEmpty()) {

          if (myForceMode.isForce() || pushingToNotTrackedBranch(result.rejected) || pushingNotCurrentBranch(result.rejected)) break;

          // propose to update if rejected
          if (pushAttempt == 0 && !mySettings.autoUpdateIfPushRejected()) {
            // the dialog will be shown => check for rebase-over-merge problem in advance to avoid showing several dialogs in a row
            rebaseOverMergeProblemDetected = !findRootsWithMergeCommits(myRepositoryManager.getRepositories()).isEmpty();

            updateSettings = showDialogAndGetExitCode(result.rejected.keySet(), updateSettings,
                                                      rebaseOverMergeProblemDetected.booleanValue());
            if (updateSettings == null) break;
            savePushUpdateSettings(updateSettings, rebaseOverMergeProblemDetected.booleanValue());
          }

          if (beforePushLabel == null) { // put the label only before the very first update
            beforePushLabel = LocalHistory.getInstance().putSystemLabel(myProject,
                                                                        GitBundle.message("push.local.history.system.label.before"));
          }
          Collection<GitRepository> rootsToUpdate = myRepositoryManager.getRepositories();
          LOG.debug("roots to update: " + rootsToUpdate);
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
        afterPushLabel = LocalHistory.getInstance().putSystemLabel(myProject, GitBundle.message("push.local.history.system.label.after"));
      }
      for (GitRepository repository : myPushSpecs.keySet()) {
        repository.update();
      }
    }
    return prepareCombinedResult(results, updatedRoots, preUpdatePositions, beforePushLabel, afterPushLabel);
  }

  @NotNull
  private Collection<VirtualFile> findRootsWithMergeCommits(@NotNull Collection<? extends GitRepository> rootsToSearch) {
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

  @NotNull
  public GitPushOperation deriveForceWithoutLease(@NotNull List<GitRepository> newRepositories) {
    Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> newPushSpec = filter(myPushSpecs, repo -> newRepositories.contains(repo));
    return new GitPushOperation(myProject, myPushSupport, newPushSpec, myTagMode, ForceMode.FORCE, mySkipHook);
  }

  private static boolean pushingToNotTrackedBranch(@NotNull Map<GitRepository, GitPushRepoResult> rejected) {
    boolean pushingToNotTrackedBranch = ContainerUtil.exists(rejected.entrySet(), entry -> {
      GitRepository repository = entry.getKey();
      GitLocalBranch currentBranch = repository.getCurrentBranch();
      assert currentBranch != null;
      GitBranchTrackInfo trackInfo = GitBranchUtil.getTrackInfoForBranch(repository, currentBranch);
      return trackInfo == null || !trackInfo.getRemoteBranch().getFullName().equals(entry.getValue().getTargetBranch());
    });
    LOG.debug("Pushing to not tracked branch condition is [" + pushingToNotTrackedBranch + "]");
    return pushingToNotTrackedBranch;
  }

  private static boolean pushingNotCurrentBranch(@NotNull Map<GitRepository, GitPushRepoResult> rejected) {
    boolean pushingNotCurrentBranch = ContainerUtil.exists(rejected.entrySet(), entry -> {
      GitRepository repository = entry.getKey();
      String currentBranch = Objects.requireNonNull(repository.getCurrentBranch()).getFullName();
      return !StringUtil.equals(currentBranch, entry.getValue().getSourceBranch());
    });
    LOG.debug("Pushing non current branch condition is [" + pushingNotCurrentBranch + "]");
    return pushingNotCurrentBranch;
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
    Map<GitRepository, GitPushRepoResult> results = new HashMap<>();
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

    return new GitPushResult(results, updatedFiles, beforeUpdateLabel, afterUpdateLabel, myUpdatedRanges);
  }

  @NotNull
  private Map<GitRepository, GitPushRepoResult> push(@NotNull List<? extends GitRepository> repositories) {
    Map<GitRepository, GitPushRepoResult> results = new LinkedHashMap<>();
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
        final GitPushNativeResult sourceResult = getPushedBranchOrCommit(nativeResults);
        if (sourceResult == null) {
          LOG.error("No result for branch or commit among: [" + nativeResults + "]\n" +
                    "Full result: " + resultWithOutput);
          continue;
        }
        List<GitPushNativeResult> tagResults = filter(nativeResults, result ->
          !result.equals(sourceResult) && (result.getType() == NEW_REF || result.getType() == FORCED_UPDATE));
        int commits = collectNumberOfPushedCommits(repository.getRoot(), sourceResult);
        repoResult = GitPushRepoResult.convertFromNative(sourceResult, tagResults, commits, source, target.getBranch());
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
  private static GitPushNativeResult getPushedBranchOrCommit(@NotNull List<? extends GitPushNativeResult> results) {
    return ContainerUtil.find(results, result -> isBranch(result) || isHash(result) || isHeadRelativeReference(result));
  }

  private static boolean isBranch(@NotNull GitPushNativeResult result) {
    String sourceRef = result.getSourceRef();
    return sourceRef.startsWith("refs/heads/") || HASH_REGEX.matcher(sourceRef).matches();
  }

  private static boolean isHash(@NotNull GitPushNativeResult result) {
    String sourceRef = result.getSourceRef();
    return HASH_REGEX.matcher(sourceRef).matches();
  }

  private static boolean isHeadRelativeReference(@NotNull GitPushNativeResult result) {
    String sourceRef = result.getSourceRef();
    return sourceRef.startsWith(HEAD);
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
    try {
      new MergeChangeCollector(myProject, repository, new GitRevisionNumber(preUpdatePosition)).collect(updatedFiles);
    }
    catch (VcsException e) {
      LOG.info(e);
    }
  }

  @NotNull
  private ResultWithOutput doPush(@NotNull GitRepository repository, @NotNull PushSpec<GitPushSource, GitPushTarget> pushSpec) {
    GitPushTarget target = pushSpec.getTarget();
    GitPushSource gitPushSource = pushSpec.getSource();
    GitLocalBranch sourceBranch = gitPushSource.getBranch();
    GitRemoteBranch targetBranch = target.getBranch();

    GitLineHandlerListener progressListener = GitStandardProgressAnalyzer.createListener(myProgressIndicator);
    boolean setUpstream = target.isNewBranchCreated() && !branchTrackingInfoIsSet(repository, sourceBranch);
    String tagMode = myTagMode == null ? null : myTagMode.getArgument();

    String remoteBranchName = targetBranch.getNameForRemoteOperations();
    String targetRef = createReferenceForNewTargetBranch(remoteBranchName, setUpstream);
    String spec = gitPushSource.getRevision() + ":" + targetRef;
    GitRemote remote = targetBranch.getRemote();

    List<GitPushParams.ForceWithLease> forceWithLease = emptyList();
    if (myForceMode == ForceMode.FORCE_WITH_LEASE) {
      Hash hash = repository.getBranches().getHash(targetBranch);
      String expectedHash = hash != null ? hash.asString() : "";
      forceWithLease = singletonList(new ForceWithLeaseReference(targetBranch.getNameForRemoteOperations(), expectedHash));
    }

    GitPushParamsImpl params = new GitPushParamsImpl(remote, spec, myForceMode.isForce(), setUpstream, mySkipHook, tagMode, forceWithLease);

    GitCommandResult res = myGit.push(repository, params, progressListener);

    if (res.success()) {
      BackgroundTaskUtil.syncPublisher(myProject, GIT_AUTHENTICATION_SUCCESS).authenticationSucceeded(repository, remote);
    }
    return new ResultWithOutput(res);
  }

  private static boolean isParticularReferenceSpecified(@NotNull String remoteBranchName) {
    return remoteBranchName.startsWith("refs/");
  }

  private static String createReferenceForNewTargetBranch(@NotNull String remoteBranchName, boolean setUpstream) {
    if (!isParticularReferenceSpecified(remoteBranchName) && setUpstream) {
      return "refs/heads/" + remoteBranchName;
    }

    return remoteBranchName;
  }

  private static boolean branchTrackingInfoIsSet(@NotNull GitRepository repository, @NotNull final GitLocalBranch source) {
    return ContainerUtil.exists(repository.getBranchTrackInfos(), info -> info.getLocalBranch().equals(source));
  }

  private void savePushUpdateSettings(@NotNull PushUpdateSettings settings, boolean rebaseOverMergeDetected) {
    UpdateMethod updateMethod = settings.getUpdateMethod();
    if (!rebaseOverMergeDetected // don't overwrite explicit "rebase" with temporary "merge" caused by merge commits
        && mySettings.getUpdateMethod() != updateMethod && mySettings.getUpdateMethod() != UpdateMethod.BRANCH_DEFAULT) { // don't overwrite "branch default" setting
      mySettings.setUpdateMethod(updateMethod);
    }
  }

  @NotNull
  private PushUpdateSettings readPushUpdateSettings() {
    UpdateMethod updateMethod = mySettings.getUpdateMethod();
    if (updateMethod == UpdateMethod.BRANCH_DEFAULT) {
      // deliberate limitation: we have only 2 buttons => choose method from the 1st repo if different
      updateMethod = GitUpdater.resolveUpdateMethod(myPushSpecs.keySet().iterator().next());
    }
    return new PushUpdateSettings(updateMethod);
  }

  @Nullable
  private PushUpdateSettings showDialogAndGetExitCode(@NotNull final Set<? extends GitRepository> repositories,
                                                      @NotNull final PushUpdateSettings initialSettings,
                                                      final boolean rebaseOverMergeProblemDetected) {
    Ref<PushUpdateSettings> updateSettings = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      GitRejectedPushUpdateDialog dialog = new GitRejectedPushUpdateDialog(
        myProject, repositories, initialSettings, rebaseOverMergeProblemDetected
      );

      PushRejectedExitCode exitCode = dialog.showAndGet();
      if (!exitCode.equals(PushRejectedExitCode.CANCEL)) {
        mySettings.setAutoUpdateIfPushRejected(dialog.getShouldAutoUpdateInFuture());
        updateSettings.set(new PushUpdateSettings(convertUpdateMethodFromDialogExitCode(exitCode)));
      }
    });
    return updateSettings.get();
  }

  @NotNull
  private static UpdateMethod convertUpdateMethodFromDialogExitCode(PushRejectedExitCode exitCode) {
    switch (exitCode) {
      case MERGE: return UpdateMethod.MERGE;
      case REBASE: return UpdateMethod.REBASE;
      default: throw new IllegalStateException("Unexpected exit code: " + exitCode);
    }
  }

  @NotNull
  protected GitUpdateResult update(@NotNull Collection<? extends GitRepository> rootsToUpdate,
                                   @NotNull UpdateMethod updateMethod,
                                   boolean checkForRebaseOverMergeProblem) {
    GitUpdateProcess updateProcess = new GitUpdateProcess(myProject, myProgressIndicator,
                                                          new HashSet<>(rootsToUpdate), UpdatedFiles.create(), null,
                                                          checkForRebaseOverMergeProblem, false);
    GitUpdateResult updateResult = updateProcess.update(updateMethod);
    Map<GitRepository, HashRange> ranges = updateProcess.getUpdatedRanges();
    if (ranges != null) { // normally shouldn't happen, because it means that update didn't even start, e.g. in the NOT_READY situation
      joinUpdatedRanges(ranges);
    }

    for (GitRepository repository : rootsToUpdate) {
      repository.getRoot().refresh(true, true);
      repository.update();
    }
    return updateResult;
  }

  private void joinUpdatedRanges(@NotNull Map<GitRepository, HashRange> newRanges) {
    for (GitRepository repository : newRanges.keySet()) {
      HashRange newRange = newRanges.get(repository);
      HashRange current = myUpdatedRanges.get(repository);
      HashRange joinedRange = current == null ? newRange : new HashRange(current.getStart(), newRange.getEnd());
      myUpdatedRanges.put(repository, joinedRange);
    }
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

  enum ForceMode {
    NONE, FORCE, FORCE_WITH_LEASE;

    public boolean isForce() {
      return this != NONE;
    }
  }
}
