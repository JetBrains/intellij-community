// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.PushDivergence;
import com.intellij.dvcs.push.PushSpec;
import com.intellij.dvcs.repo.Repository;
import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.internal.statistic.StructuredIdeActivity;
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
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitOperationsCollector;
import git4idea.GitRemoteBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
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
import git4idea.update.GitRebaseOverMergeProblem;
import git4idea.update.GitUpdateProcess;
import git4idea.update.GitUpdateResult;
import git4idea.update.GitUpdater;
import git4idea.update.HashRange;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.filter;
import static com.intellij.vcs.log.util.VcsLogUtil.HEAD;
import static git4idea.commands.GitAuthenticationListener.GIT_AUTHENTICATION_SUCCESS;
import static git4idea.push.GitPushNativeResult.Type.FORCED_UPDATE;
import static git4idea.push.GitPushNativeResult.Type.NEW_REF;
import static git4idea.push.GitPushRepoResult.Type.NOT_PUSHED;
import static git4idea.push.GitPushRepoResult.Type.REJECTED_NO_FF;
import static git4idea.push.GitPushRepoResult.tagPushResult;
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
  private final @NotNull GitPushSupport myPushSupport;
  private final @Unmodifiable Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> myPushSpecs;
  private final @Nullable GitPushTagMode myTagMode;
  private final ForceMode myForceMode;
  private final boolean mySkipHook;
  private final Git myGit;
  private final ProgressIndicator myProgressIndicator;
  private final GitVcsSettings mySettings;
  private final GitRepositoryManager myRepositoryManager;
  private final @NotNull Map<GitRepository, HashRange> myUpdatedRanges = new LinkedHashMap<>();
  private final @NotNull Map<GitRepository, Hash> myExpectedRemoteHashes = new HashMap<>();
  private final @NotNull Map<GitRepository, RejectedPushDivergence> myRejectedDivergences = new HashMap<>();
  private final boolean myAllowForceOnRejection;

  public GitPushOperation(@NotNull Project project,
                          @NotNull GitPushSupport pushSupport,
                          @NotNull Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> pushSpecs,
                          @Nullable GitPushTagMode tagMode,
                          boolean force,
                          boolean skipHook) {
    this(project, pushSupport, pushSpecs, tagMode, getForceMode(force), skipHook);
  }

  private static @NotNull ForceMode getForceMode(boolean force) {
    if (force) return AdvancedSettings.getBoolean("git.use.push.force.with.lease") ? ForceMode.FORCE_WITH_LEASE : ForceMode.FORCE;
    return ForceMode.NONE;
  }

  GitPushOperation(@NotNull Project project,
                   @NotNull GitPushSupport pushSupport,
                   @NotNull @Unmodifiable Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> pushSpecs,
                   @Nullable GitPushTagMode tagMode,
                   @NotNull ForceMode forceMode,
                   boolean skipHook) {
    this(project, pushSupport, pushSpecs, tagMode, forceMode, skipHook, Map.of(), false);
  }

  /**
   * @param expectedRemoteHashes  the positions of the targets that the user saw before the push.
   *                              A force push with lease expects the remote to be at these positions.
   * @param allowForceOnRejection true to offer a force push when the remote rejects the push
   */
  @ApiStatus.Internal
  public GitPushOperation(@NotNull Project project,
                          @NotNull GitPushSupport pushSupport,
                          @NotNull @Unmodifiable Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> pushSpecs,
                          @Nullable GitPushTagMode tagMode,
                          boolean force,
                          boolean skipHook,
                          @NotNull Map<GitRepository, Hash> expectedRemoteHashes,
                          boolean allowForceOnRejection) {
    this(project, pushSupport, pushSpecs, tagMode, getForceMode(force), skipHook, expectedRemoteHashes, allowForceOnRejection);
  }

  private GitPushOperation(@NotNull Project project,
                           @NotNull GitPushSupport pushSupport,
                           @NotNull @Unmodifiable Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> pushSpecs,
                           @Nullable GitPushTagMode tagMode,
                           @NotNull ForceMode forceMode,
                           boolean skipHook,
                           @NotNull Map<GitRepository, Hash> expectedRemoteHashes,
                           boolean allowForceOnRejection) {
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
    myExpectedRemoteHashes.putAll(expectedRemoteHashes);
    myAllowForceOnRejection = allowForceOnRejection;
  }

  public @NotNull GitPushResult execute() {
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

          if (myForceMode.isForce() ||
              pushingToNotTrackedBranch(result.rejected) ||
              pushingNotCurrentBranch(result.rejected)) {
            break;
          }

          // propose to update if rejected
          if (pushAttempt == 0 && !mySettings.autoUpdateIfPushRejected()) {
            // the dialog will be shown => check for rebase-over-merge problem in advance to avoid showing several dialogs in a row
            rebaseOverMergeProblemDetected = !findRootsWithMergeCommits(myRepositoryManager.getRepositories()).isEmpty();
            Map<GitRepository, RejectedPushDivergence> divergences = detectRejectedDivergences(result.rejected);

            RejectedPushChoice choice = showDialogAndGetExitCode(result.rejected.keySet(), updateSettings,
                                                                 rebaseOverMergeProblemDetected.booleanValue(), divergences);
            if (choice == null) break;
            if (choice.forcePush()) {
              forcePushRejected(divergences, results);
              break;
            }
            updateSettings = Objects.requireNonNull(choice.updateSettings());
            savePushUpdateSettings(updateSettings, rebaseOverMergeProblemDetected.booleanValue());
          }

          if (beforePushLabel == null) { // put the label only before the very first update
            beforePushLabel = LocalHistory.getInstance().putSystemLabel(myProject,
                                                                        GitBundle.message("push.local.history.system.label.before"));
          }
          Collection<GitRepository> rootsToUpdate = myRepositoryManager.getRepositories();
          LOG.debug("roots to update: " + rootsToUpdate);
          GitUpdateResult updateResult = update(rootsToUpdate, updateSettings.getUpdateMethod(), rebaseOverMergeProblemDetected == null);
          LOG.debug("update result: " + updateResult);
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
    collectRejectedDivergences(results, updatedRoots);
    recordRecentPushTargets(results);
    return prepareCombinedResult(results, updatedRoots, preUpdatePositions, beforePushLabel, afterPushLabel);
  }

  /**
   * Remember the push target of each successful result, so that the push dialog can suggest it later.
   * Skip the target that the dialog computes as the default.
   */
  private void recordRecentPushTargets(@NotNull Map<GitRepository, GitPushRepoResult> results) {
    for (Map.Entry<GitRepository, GitPushRepoResult> entry : results.entrySet()) {
      GitRepository repository = entry.getKey();
      if (!isSuccessfulPush(entry.getValue())) continue;

      PushSpec<GitPushSource, GitPushTarget> spec = myPushSpecs.get(repository);
      if (spec == null) continue;

      GitLocalBranch sourceBranch = spec.getSource().getBranch();
      GitPushTarget target = spec.getTarget();
      if (sourceBranch == null || target.isSpecialRef()) continue;

      GitPushTarget defaultTarget = myPushSupport.getDefaultTarget(repository, spec.getSource());
      if (defaultTarget != null && defaultTarget.getBranch().equals(target.getBranch())) continue;

      GitRemoteBranch targetBranch = target.getBranch();
      mySettings.addRecentPushTarget(repository.getRoot().getPath(), sourceBranch.getName(),
                                     targetBranch.getRemote().getName(), targetBranch.getNameForRemoteOperations());
    }
  }

  private static boolean isSuccessfulPush(@NotNull GitPushRepoResult result) {
    return switch (result.getType()) {
      case SUCCESS, NEW_BRANCH, FORCED, UP_TO_DATE -> true;
      default -> false;
    };
  }

  private @NotNull Collection<VirtualFile> findRootsWithMergeCommits(@NotNull Collection<? extends GitRepository> rootsToSearch) {
    return ContainerUtil.mapNotNull(rootsToSearch, repo -> {
      PushSpec<GitPushSource, GitPushTarget> pushSpec = myPushSpecs.get(repo);
      if (pushSpec == null) { // repository is not selected to be pushed, but can be rebased
        GitPushSource source = myPushSupport.getSource(repo);
        GitPushTarget target = myPushSupport.getDefaultTarget(repo);
        if (source == null || target == null) {
          return null;
        }
        pushSpec = new PushSpec<>(source, target);
      }
      String baseRef = pushSpec.getTarget().getBranch().getFullName();
      String currentRef = pushSpec.getSource().getRevision();
      return GitRebaseOverMergeProblem.hasProblem(myProject, repo.getRoot(), baseRef, currentRef) ? repo.getRoot() : null;
    });
  }

  /**
   * Creates a force push of the given repositories.
   * The force push expects the remote at the position that the user saw, if the settings allow a force push with lease.
   */
  @ApiStatus.Internal
  public @NotNull GitPushOperation deriveForcePush(@NotNull List<GitRepository> newRepositories) {
    Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> newPushSpec = filter(myPushSpecs, newRepositories::contains);
    Map<GitRepository, Hash> expectedRemoteHashes = new HashMap<>(filter(myExpectedRemoteHashes, newRepositories::contains));
    for (GitRepository repository : newRepositories) {
      RejectedPushDivergence divergence = myRejectedDivergences.get(repository);
      Hash remoteTip = divergence != null ? divergence.getDivergence().getRemoteTip() : null;
      if (remoteTip != null) {
        expectedRemoteHashes.put(repository, remoteTip);
      }
    }
    return new GitPushOperation(myProject, myPushSupport, newPushSpec, myTagMode, getForceMode(true), mySkipHook, expectedRemoteHashes,
                                false);
  }

  boolean isForcePushAllowed(@NotNull GitRepository repository) {
    PushSpec<GitPushSource, GitPushTarget> spec = myPushSpecs.get(repository);
    return spec != null && myPushSupport.isForcePushAllowed(repository, spec.getTarget());
  }

  @Nullable GitPushSource getSource(@NotNull GitRepository repository) {
    PushSpec<GitPushSource, GitPushTarget> spec = myPushSpecs.get(repository);
    return spec != null ? spec.getSource() : null;
  }

  @Nullable GitPushTarget getTarget(@NotNull GitRepository repository) {
    PushSpec<GitPushSource, GitPushTarget> spec = myPushSpecs.get(repository);
    return spec != null ? spec.getTarget() : null;
  }

  public @NotNull GitPushOperation deriveForceWithoutLease(@NotNull List<GitRepository> newRepositories) {
    Map<GitRepository, PushSpec<GitPushSource, GitPushTarget>> newPushSpec = filter(myPushSpecs, repo -> newRepositories.contains(repo));
    return new GitPushOperation(myProject, myPushSupport, newPushSpec, myTagMode, ForceMode.FORCE, mySkipHook);
  }

  private static boolean pushingToNotTrackedBranch(@NotNull Map<GitRepository, GitPushRepoResult> rejected) {
    boolean pushingToNotTrackedBranch = ContainerUtil.exists(rejected.entrySet(), entry -> {
      GitRepository repository = entry.getKey();
      GitLocalBranch currentBranch = repository.getCurrentBranch();
      if (currentBranch == null) return true;
      GitBranchTrackInfo trackInfo = GitBranchUtil.getTrackInfoForBranch(repository, currentBranch);
      return trackInfo == null || !trackInfo.getRemoteBranch().getFullName().equals(entry.getValue().getTargetBranch());
    });
    LOG.debug("Pushing to not tracked branch condition is [" + pushingToNotTrackedBranch + "]");
    return pushingToNotTrackedBranch;
  }

  private static boolean pushingNotCurrentBranch(@NotNull Map<GitRepository, GitPushRepoResult> rejected) {
    boolean pushingNotCurrentBranch = ContainerUtil.exists(rejected.entrySet(), entry -> {
      GitRepository repository = entry.getKey();
      GitLocalBranch currentBranch = repository.getCurrentBranch();
      String pushedBranch = entry.getValue().getSourceBranch();
      return currentBranch == null || !StringUtil.equals(currentBranch.getFullName(), pushedBranch);
    });
    LOG.debug("Pushing non current branch condition is [" + pushingNotCurrentBranch + "]");
    return pushingNotCurrentBranch;
  }

  private static @NotNull List<GitRepository> getRejectedAndNotPushed(final @NotNull Map<GitRepository, GitPushRepoResult> results) {
    return filter(results.keySet(),
                  repository -> results.get(repository).getType() == REJECTED_NO_FF ||
                                results.get(repository).getType() == NOT_PUSHED);
  }

  private @NotNull Map<GitRepository, String> updateRootInfoAndRememberPositions() {
    Set<GitRepository> repositories = myPushSpecs.keySet();
    repositories.forEach(GitRepository::update);
    return StreamEx.of(repositories).toMap(Repository::getCurrentRevision);
  }

  private @NotNull GitPushResult prepareCombinedResult(@NotNull Map<GitRepository, GitPushRepoResult> allRoots,
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

  private @NotNull Map<GitRepository, GitPushRepoResult> push(@NotNull List<? extends GitRepository> repositories) {
    return push(repositories, myForceMode);
  }

  private @NotNull Map<GitRepository, GitPushRepoResult> push(@NotNull List<? extends GitRepository> repositories,
                                                              @NotNull ForceMode forceMode) {
    Map<GitRepository, GitPushRepoResult> results = new LinkedHashMap<>();
    for (GitRepository repository : repositories) {
      PushSpec<GitPushSource, GitPushTarget> spec = myPushSpecs.get(repository);
      ResultWithOutput resultWithOutput = null;
      GitPushRepoResult repoResult = null;

      StructuredIdeActivity pushActivity = GitOperationsCollector.startLogPush(repository.getProject());
      boolean setUpstream = spec.getTarget().shouldSetUpstream(spec.getSource(), repository);
      try {
        resultWithOutput = doPush(repository, spec, setUpstream, forceMode);
        LOG.debug("Pushed to " + DvcsUtil.getShortRepositoryName(repository) + ": " + resultWithOutput);

        GitPushSource pushSource = spec.getSource();
        GitPushTarget target = spec.getTarget();
        if (resultWithOutput.isError()) {
          repoResult = GitPushRepoResult.error(pushSource, target.getBranch(), resultWithOutput.getErrorAsString());
        }
        else {
          List<GitPushNativeResult> nativeResults = resultWithOutput.parsedResults;

          if (pushSource instanceof GitPushSource.Tag tagPushSource) {
            repoResult = tagPushResult(ContainerUtil.getOnlyItem(nativeResults), tagPushSource, target.getBranch());
          }
          else {
            GitPushNativeResult sourceResult = getPushedBranchOrCommit(nativeResults);
            if (sourceResult == null) {
              LOG.error("No result for branch or commit among: [" + nativeResults + "]\n" +
                        "Full result: " + resultWithOutput);
              continue;
            }
            List<GitPushNativeResult> tagResults = filter(nativeResults, result ->
              !result.equals(sourceResult) && (result.getType() == NEW_REF || result.getType() == FORCED_UPDATE));
            int commits = collectNumberOfPushedCommits(repository.getRoot(), sourceResult);
            repoResult = GitPushRepoResult.convertFromNative(sourceResult, tagResults, commits, pushSource, target.getBranch());
          }
        }
      }
      finally {
        GitOperationsCollector.endLogPush(
          pushActivity,
          resultWithOutput != null ? resultWithOutput.resultOutput : null,
          repoResult,
          getPushTargetType(repository, spec),
          spec.getTarget().isNewBranchCreated(),
          setUpstream
        );
      }

      LOG.debug("Converted result: " + repoResult);
      results.put(repository, repoResult);
    }

    // fill other not-processed repositories as not-pushed
    for (GitRepository repository : repositories) {
      if (!results.containsKey(repository)) {
        PushSpec<GitPushSource, GitPushTarget> spec = myPushSpecs.get(repository);
        results.put(repository, GitPushRepoResult.notPushed(spec.getSource(), spec.getTarget().getBranch()));
      }
    }
    return results;
  }

  private @Nullable GitPushTargetType getPushTargetType(GitRepository repository, PushSpec<GitPushSource, GitPushTarget> spec) {
    GitPushSource pushSource = spec.getSource();
    GitPushTarget target = spec.getTarget();

    GitPushTargetType targetType = spec.getTarget().getTargetType();
    // Effective target branch can still be a tracking branch or a branch from spec
    // E.g., user changed branch to custom in a push dialog and then reverted it to default
    if (targetType == GitPushTargetType.CUSTOM) {
      GitLocalBranch sourceBranch = pushSource.getBranch();
      if (sourceBranch != null) {
        GitPushTarget defaultTarget = myPushSupport.getDefaultTarget(repository, pushSource);
        if (defaultTarget != null && defaultTarget.getBranch().equals(target.getBranch())) {
          targetType = defaultTarget.getTargetType();
        }
      }
    }

    return targetType;
  }

  private static @Nullable GitPushNativeResult getPushedBranchOrCommit(@NotNull List<? extends GitPushNativeResult> results) {
    return ContainerUtil.find(results, result -> isBranch(result) || isHash(result) || isHeadRelativeReference(result));
  }

  private static boolean isBranch(@NotNull GitPushNativeResult result) {
    String sourceRef = result.getSourceRef();
    return sourceRef.startsWith(GitBranch.REFS_HEADS_PREFIX) || GitUtil.isPossibleHash(sourceRef);
  }

  private static boolean isHash(@NotNull GitPushNativeResult result) {
    String sourceRef = result.getSourceRef();
    return GitUtil.isPossibleHash(sourceRef);
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
      LOG.error("Couldn't collect commits from range " + range, e);
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

  private @NotNull ResultWithOutput doPush(@NotNull GitRepository repository,
                                          @NotNull PushSpec<GitPushSource, GitPushTarget> pushSpec,
                                          boolean setUpstream,
                                          @NotNull ForceMode forceMode) {
    GitPushTarget pushTarget = pushSpec.getTarget();
    GitRemoteBranch targetBranch = pushTarget.getBranch();

    GitLineHandlerListener progressListener = GitStandardProgressAnalyzer.createListener(myProgressIndicator);
    String tagMode = myTagMode == null ? null : myTagMode.getArgument();

    String spec = createPushSpec(pushSpec.getSource(), pushTarget, setUpstream);
    GitRemote remote = targetBranch.getRemote();

    List<GitPushParams.ForceWithLease> forceWithLease = emptyList();
    if (forceMode == ForceMode.FORCE_WITH_LEASE) {
      Hash expectedHash = myExpectedRemoteHashes.get(repository);
      Hash hash = expectedHash != null ? expectedHash : repository.getBranches().getHash(targetBranch);
      String expectedValue = hash != null ? hash.asString() : "";
      forceWithLease = singletonList(new ForceWithLeaseReference(targetBranch.getNameForRemoteOperations(), expectedValue));
    }

    GitPushParamsImpl params = new GitPushParamsImpl(remote, spec, forceMode.isForce(), setUpstream, mySkipHook, tagMode, forceWithLease);

    GitCommandResult res = myGit.push(repository, params, progressListener);

    if (res.success()) {
      BackgroundTaskUtil.syncPublisher(myProject, GIT_AUTHENTICATION_SUCCESS).authenticationSucceeded(repository, remote);
    }
    return new ResultWithOutput(res);
  }

  private static String createPushSpec(@NotNull GitPushSource source,
                                       @NotNull GitPushTarget target,
                                       boolean setUpstream) {
    boolean needFullRefName = setUpstream || !source.isBranchRef();
    String remoteBranchName = target.getBranch().getNameForRemoteOperations();
    String targetRef = needFullRefName && !remoteBranchName.startsWith("refs/")
                       ? GitBranch.REFS_HEADS_PREFIX + remoteBranchName
                       : remoteBranchName;

    return source.getRevision() + ":" + targetRef;
  }

  private static boolean branchTrackingInfoIsSet(@NotNull GitRepository repository, final @NotNull GitLocalBranch source) {
    return ContainerUtil.exists(repository.getBranchTrackInfos(), info -> info.getLocalBranch().equals(source));
  }

  private void savePushUpdateSettings(@NotNull PushUpdateSettings settings, boolean rebaseOverMergeDetected) {
    UpdateMethod updateMethod = settings.getUpdateMethod();
    if (!rebaseOverMergeDetected && // don't overwrite explicit "rebase" with temporary "merge" caused by merge commits
        mySettings.getUpdateMethod() != updateMethod &&
        mySettings.getUpdateMethod() != UpdateMethod.BRANCH_DEFAULT) { // don't overwrite "branch default" setting
      mySettings.setUpdateMethod(updateMethod);
    }
  }

  private @NotNull PushUpdateSettings readPushUpdateSettings() {
    UpdateMethod updateMethod = mySettings.getUpdateMethod();
    if (updateMethod == UpdateMethod.BRANCH_DEFAULT) {
      // deliberate limitation: we have only 2 buttons => choose method from the 1st repo if different
      updateMethod = GitUpdater.resolveUpdateMethod(myPushSpecs.keySet().iterator().next());
    }
    return new PushUpdateSettings(updateMethod);
  }

  /**
   * Finds the remote commits of the rejected pushes, if a force push can be offered for each of them.
   */
  private @NotNull Map<GitRepository, RejectedPushDivergence> detectRejectedDivergences(@NotNull Map<GitRepository, GitPushRepoResult> rejected) {
    Map<GitRepository, RejectedPushDivergence> divergences = new LinkedHashMap<>();
    for (Map.Entry<GitRepository, GitPushRepoResult> entry : rejected.entrySet()) {
      RejectedPushDivergence divergence = detectRejectedDivergence(entry.getKey(), entry.getValue());
      if (divergence == null) return Map.of();
      divergences.put(entry.getKey(), divergence);
    }
    return divergences;
  }

  /**
   * Finds the remote commits of a rejected push, if the local repository has all of them.
   * A rejection with "fetch first" means that the local repository doesn't have the remote commits, so they can't be shown.
   */
  private @Nullable RejectedPushDivergence detectRejectedDivergence(@NotNull GitRepository repository, @NotNull GitPushRepoResult result) {
    if (!myAllowForceOnRejection || isFetchFirstRejection(result)) return null;
    RejectedPushDivergence known = myRejectedDivergences.get(repository);
    if (known != null) return known;

    PushSpec<GitPushSource, GitPushTarget> spec = myPushSpecs.get(repository);
    if (spec == null) return null;
    if (!(GitPushDivergenceDetector.getInstance(myProject).detectBlocking(repository, spec.getSource(), spec.getTarget(), false)
            instanceof PushDivergence.Diverged divergence)) {
      return null;
    }

    RejectedPushDivergence rejectedDivergence =
      new RejectedPushDivergence(spec.getSource().getPresentation(), spec.getTarget().getPresentation(), divergence);
    myRejectedDivergences.put(repository, rejectedDivergence);
    return rejectedDivergence;
  }

  /**
   * Finds the remote commits of the rejected pushes that were not updated, so that the notification can tell about them.
   */
  private void collectRejectedDivergences(@NotNull Map<GitRepository, GitPushRepoResult> results,
                                          @NotNull Map<GitRepository, GitUpdateResult> updatedRoots) {
    for (Map.Entry<GitRepository, GitPushRepoResult> entry : results.entrySet()) {
      if (entry.getValue().getType() == REJECTED_NO_FF && !updatedRoots.containsKey(entry.getKey())) {
        detectRejectedDivergence(entry.getKey(), entry.getValue());
      }
    }
  }

  static boolean isFetchFirstRejection(@NotNull GitPushRepoResult result) {
    String reason = result.getError();
    return result.getType() == REJECTED_NO_FF && reason != null && StringUtil.containsIgnoreCase(reason, GitPushNativeResult.FETCH_FIRST_REASON);
  }

  /**
   * Returns the remote commits of a rejected push, if the local repository has all of them.
   */
  @Nullable RejectedPushDivergence getRejectedDivergence(@NotNull GitRepository repository) {
    return myRejectedDivergences.get(repository);
  }

  /**
   * Pushes the rejected repositories again with force. The force push expects the remote to be at the position that the dialog showed.
   */
  private void forcePushRejected(@NotNull Map<GitRepository, RejectedPushDivergence> divergences,
                                 @NotNull Map<GitRepository, GitPushRepoResult> results) {
    for (Map.Entry<GitRepository, RejectedPushDivergence> entry : divergences.entrySet()) {
      Hash remoteTip = entry.getValue().getDivergence().getRemoteTip();
      if (remoteTip != null) {
        myExpectedRemoteHashes.put(entry.getKey(), remoteTip);
      }
    }
    LOG.debug("Force pushing rejected repositories: " + divergences.keySet());
    results.putAll(push(myRepositoryManager.sortByDependency(divergences.keySet()), getForceMode(true)));
  }

  /**
   * Returns true when the dialog can offer a force push: one repository, and lease on.
   */
  private boolean canOfferForcePush(@NotNull Map<GitRepository, RejectedPushDivergence> divergences) {
    return divergences.size() == 1 && ContainerUtil.and(divergences.entrySet(), entry ->
      entry.getValue().getDivergence().getRemoteTip() != null &&
      myPushSupport.isForcePushAllowed(entry.getKey(), myPushSpecs.get(entry.getKey()).getTarget()));
  }

  private @Nullable String findForcePushProhibitedTarget(@NotNull Map<GitRepository, RejectedPushDivergence> divergences) {
    for (GitRepository repository : divergences.keySet()) {
      GitPushTarget target = myPushSpecs.get(repository).getTarget();
      if (!myPushSupport.isForcePushAllowed(repository, target)) return target.getPresentation();
    }
    return null;
  }

  /**
   * @return the choice of the user, or {@code null} if the user canceled
   */
  private @Nullable RejectedPushChoice showDialogAndGetExitCode(final @NotNull Set<? extends GitRepository> repositories,
                                                                final @NotNull PushUpdateSettings initialSettings,
                                                                final boolean rebaseOverMergeProblemDetected,
                                                                final @NotNull Map<GitRepository, RejectedPushDivergence> divergences) {
    boolean offerForcePush = canOfferForcePush(divergences);
    String prohibitedTarget = offerForcePush ? null : findForcePushProhibitedTarget(divergences);
    Ref<RejectedPushChoice> choice = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      GitRejectedPushUpdateDialog dialog = new GitRejectedPushUpdateDialog(
        myProject, repositories, initialSettings, rebaseOverMergeProblemDetected, divergences, offerForcePush, prohibitedTarget
      );

      PushRejectedExitCode exitCode = dialog.showAndGet();
      if (exitCode == PushRejectedExitCode.FORCE_PUSH) {
        choice.set(new RejectedPushChoice(null, true));
      }
      else if (!exitCode.equals(PushRejectedExitCode.CANCEL)) {
        mySettings.setAutoUpdateIfPushRejected(dialog.getShouldAutoUpdateInFuture());
        choice.set(new RejectedPushChoice(new PushUpdateSettings(convertUpdateMethodFromDialogExitCode(exitCode)), false));
      }
    });
    return choice.get();
  }

  private record RejectedPushChoice(@Nullable PushUpdateSettings updateSettings, boolean forcePush) {
  }

  private static @NotNull UpdateMethod convertUpdateMethodFromDialogExitCode(PushRejectedExitCode exitCode) {
    return switch (exitCode) {
      case MERGE -> UpdateMethod.MERGE;
      case REBASE -> UpdateMethod.REBASE;
      default -> throw new IllegalStateException("Unexpected exit code: " + exitCode);
    };
  }

  protected @NotNull GitUpdateResult update(@NotNull Collection<? extends GitRepository> rootsToUpdate,
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
    private final @NotNull List<GitPushNativeResult> parsedResults;
    private final @NotNull GitCommandResult resultOutput;

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
