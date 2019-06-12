// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Alarm;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.vcs.log.Hash;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.commands.*;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersionSpecialty;
import git4idea.push.GitPushSupport;
import git4idea.push.GitPushTarget;
import git4idea.repo.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.intellij.util.containers.ContainerUtil.*;
import static git4idea.commands.GitAuthenticationMode.*;
import static git4idea.config.GitIncomingCheckStrategy.*;
import static git4idea.repo.GitRefUtil.addRefsHeadsPrefixIfNeeded;
import static git4idea.repo.GitRefUtil.getResolvedHashes;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;
import static one.util.streamex.StreamEx.of;

public class GitBranchIncomingOutgoingManager implements GitRepositoryChangeListener, GitAuthenticationListener {

  private static final Logger LOG = Logger.getInstance(GitBranchIncomingOutgoingManager.class);
  private static final String MAC_DEFAULT_LAUNCH = "com.apple.launchd";

  private static final boolean HAS_EXTERNAL_SSH_AGENT = hasExternalSSHAgent();

  @NotNull private final Object LOCK = new Object();
  @NotNull private final Set<GitRepository> myDirtyReposPull = new HashSet<>();
  @NotNull private final Set<GitRepository> myDirtyReposPush = new HashSet<>();
  private boolean myUseForceAuthentication;

  @NotNull private final MergingUpdateQueue myQueue;

  //store map from local branch to related cached remote branch hash per repository
  @NotNull private final Map<GitRepository, Map<GitLocalBranch, Hash>> myLocalBranchesToPull = newConcurrentMap();
  @NotNull private final Map<GitRepository, Map<GitLocalBranch, Hash>> myLocalBranchesToPush = newConcurrentMap();
  @NotNull private final MultiMap<GitRepository, GitRemote> myErrorMap = MultiMap.createConcurrentSet();
  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final GitVcsSettings myGitSettings;
  @Nullable private ScheduledFuture<?> myPeriodicalUpdater;
  @NotNull private final GitRepositoryManager myRepositoryManager;
  @Nullable private MessageBusConnection myConnection;
  @NotNull private final MultiMap<GitRepository, GitRemote> myAuthSuccessMap = MultiMap.createConcurrentSet();

  GitBranchIncomingOutgoingManager(@NotNull Project project,
                                   @NotNull Git git,
                                   @NotNull GitVcsSettings gitProjectSettings,
                                   @NotNull GitRepositoryManager repositoryManager) {
    myProject = project;
    myGit = git;
    myGitSettings = gitProjectSettings;
    myRepositoryManager = repositoryManager;

    myQueue = new MergingUpdateQueue("GitBranchIncomingOutgoingManager", 1000, true, null,
                                     myProject, null, Alarm.ThreadToUse.POOLED_THREAD);
    Disposer.register(myProject, new Disposable() {
      @Override
      public void dispose() {
        stopScheduling();
      }
    });
  }

  private static boolean hasExternalSSHAgent() {
    String ssh_auth_sock = EnvironmentUtil.getValue("SSH_AUTH_SOCK");
    if (ssh_auth_sock == null) return false;
    return !StringUtil.contains(ssh_auth_sock, MAC_DEFAULT_LAUNCH);
  }

  public boolean hasIncomingFor(@Nullable GitRepository repository, @NotNull String localBranchName) {
    return shouldCheckIncoming() && getBranchesToPull(repository).contains(new GitLocalBranch(localBranchName));
  }

  public boolean hasOutgoingFor(@Nullable GitRepository repository, @NotNull String localBranchName) {
    return getBranchesToPush(repository).contains(new GitLocalBranch(localBranchName));
  }

  public boolean shouldCheckIncoming() {
    return Registry.is("git.update.incoming.outgoing.info") && myGitSettings.getIncomingCheckStrategy() != Never;
  }

  @NotNull
  public static GitBranchIncomingOutgoingManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitBranchIncomingOutgoingManager.class);
  }

  public boolean hasAuthenticationProblems() {
    return !myErrorMap.isEmpty();
  }

  public boolean supportsIncomingOutgoing() {
    return GitVersionSpecialty.INCOMING_OUTGOING_BRANCH_INFO.existsIn(myProject);
  }

  public void activate() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      if (myConnection == null) {
        myConnection = myProject.getMessageBus().connect();
        myConnection.subscribe(GitRepository.GIT_REPO_CHANGE, this);
        myConnection.subscribe(GitAuthenticationListener.GIT_AUTHENTICATION_SUCCESS, this);
      }
      updateBranchesToPush();
      updateIncomingScheduling();
    });
  }

  public void updateIncomingScheduling() {
    if (myPeriodicalUpdater == null && shouldCheckIncoming()) {
      updateBranchesToPull(false);
      int timeout = Registry.intValue("git.update.incoming.info.time");
      myPeriodicalUpdater = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> updateBranchesToPull(false), timeout, timeout,
                                                                               TimeUnit.MINUTES);
    }
    else if (myPeriodicalUpdater != null && !shouldCheckIncoming()) {
      stopScheduling();
    }
  }

  @CalledInAwt
  private void stopScheduling() {
    if (myPeriodicalUpdater != null) {
      myPeriodicalUpdater.cancel(true);
      myPeriodicalUpdater = null;
    }
  }

  @CalledInAny
  public void forceUpdateBranchesToPull() {
   updateBranchesToPull(true);

    new Task.Backgroundable(myProject, "Update Branches Info...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Semaphore semaphore = new Semaphore(0);
        //to avoid eating events and make semaphore being released we use 'this' here instead of "update"
        myQueue.queue(Update.create(this, () -> semaphore.release()));
        myQueue.flush();

        try {
          while (true) {
            if (indicator.isCanceled()) break;
            if (semaphore.tryAcquire(100, TimeUnit.MILLISECONDS)) break;
          }
        }
        catch (InterruptedException e) {
          throw new ProcessCanceledException(e);
        }
      }
    }.queue();
  }

  private void scheduleUpdate() {
    myQueue.queue(Update.create("update", () -> {
      List<GitRepository> toPull;
      List<GitRepository> toPush;

      boolean useForceAuthentication;
      synchronized (LOCK) {
        toPull = new ArrayList<>(myDirtyReposPull);
        toPush = new ArrayList<>(myDirtyReposPush);
        useForceAuthentication = myUseForceAuthentication;

        myDirtyReposPull.clear();
        myDirtyReposPush.clear();
        myUseForceAuthentication = false;
      }

      BackgroundTaskUtil.runUnderDisposeAwareIndicator(myProject, () -> {
        for (GitRepository r : toPush) {
          myLocalBranchesToPush.put(r, calculateBranchesToPush(r));
        }
        for (GitRepository r : toPull) {
          myLocalBranchesToPull.put(r, calculateBranchesToPull(r, useForceAuthentication));
        }
      });
    }));
  }

  @NotNull
  public Collection<GitLocalBranch> getBranchesToPull(@Nullable GitRepository repository) {
    return getBranches(repository, myLocalBranchesToPull);
  }

  @NotNull
  public Collection<GitLocalBranch> getBranchesToPush(@Nullable GitRepository repository) {
    return getBranches(repository, myLocalBranchesToPush);
  }

  private void updateBranchesToPull(boolean useForceAuthentication) {
    if (!shouldCheckIncoming()) return;
    synchronized (LOCK) {
      if (useForceAuthentication) myUseForceAuthentication = true;
      myDirtyReposPull.addAll(myRepositoryManager.getRepositories());
    }
    scheduleUpdate();
  }

  private void updateBranchesToPush() {
    synchronized (LOCK) {
      myDirtyReposPush.addAll(myRepositoryManager.getRepositories());
    }
    scheduleUpdate();
  }

  @NotNull
  private Map<GitLocalBranch, Hash> calculateBranchesToPull(@NotNull GitRepository repository, boolean useForceAuthentication) {
    Map<GitLocalBranch, Hash> result = new HashMap<>();
    groupTrackInfoByRemotes(repository).entrySet()
      .forEach(entry -> result.putAll(calcBranchesToPullForRemote(repository, entry.getKey(), entry.getValue(),
                                                                  getAuthenticationMode(repository, entry.getKey(), useForceAuthentication))));
    return result;
  }

  @NotNull
  private Map<GitLocalBranch, Hash> calcBranchesToPullForRemote(@NotNull GitRepository repository,
                                                                @NotNull GitRemote gitRemote,
                                                                @NotNull Collection<? extends GitBranchTrackInfo> trackInfoList,
                                                                GitAuthenticationMode mode) {
    Map<GitLocalBranch, Hash> result = new HashMap<>();
    GitBranchesCollection branchesCollection = repository.getBranches();
    final Map<String, Hash> remoteNameWithHash =
      lsRemote(repository, gitRemote, map(trackInfoList, info -> info.getRemoteBranch().getNameForRemoteOperations()), mode);

    for (Map.Entry<String, Hash> hashEntry : remoteNameWithHash.entrySet()) {
      String remoteBranchName = hashEntry.getKey();
      Hash remoteHash = hashEntry.getValue();
      trackInfoList.forEach(info -> {
        GitRemoteBranch remoteBranch = info.getRemoteBranch();
        Hash localHashForRemoteBranch = branchesCollection.getHash(remoteBranch);
        Hash localHash = branchesCollection.getHash(info.getLocalBranch());

        if (localHashForRemoteBranch == null) return;

        if (StringUtil.equals(remoteBranchName, addRefsHeadsPrefixIfNeeded(remoteBranch.getNameForRemoteOperations()))) {
          if (!localHashForRemoteBranch.equals(remoteHash)) {
            result.put(info.getLocalBranch(), localHashForRemoteBranch);
          }
          else if (hasCommitsForBranch(repository, info.getLocalBranch(), localHash, localHashForRemoteBranch, true)) {
            result.put(info.getLocalBranch(), localHashForRemoteBranch);
          }
        }
      });
    }
    return result;
  }

  @NotNull
  private GitAuthenticationMode getAuthenticationMode(@NotNull GitRepository repository,
                                                      @NotNull GitRemote remote,
                                                      boolean useForceAuthentication) {
    if (useForceAuthentication) return FULL;
    if (myAuthSuccessMap.get(repository).contains(remote)) return SILENT;
    return NONE;
  }

  private boolean shouldAvoidUserInteraction(@NotNull GitRemote remote) {
    return myGitSettings.getIncomingCheckStrategy() == Auto && HAS_EXTERNAL_SSH_AGENT && containsSSHUrl(remote);
  }

  private static boolean containsSSHUrl(@NotNull GitRemote remote) {
    return exists(remote.getUrls(), url -> !url.startsWith(URLUtil.HTTP_PROTOCOL));
  }

  @NotNull
  private Map<String, Hash> lsRemote(@NotNull GitRepository repository,
                                     @NotNull GitRemote remote,
                                     @NotNull List<String> branchRefNames,
                                     @NotNull GitAuthenticationMode authenticationMode) {
    Map<String, Hash> result = new HashMap<>();

    if (!supportsIncomingOutgoing()) return result;
    if (authenticationMode == NONE || (authenticationMode == SILENT && shouldAvoidUserInteraction(remote))) {
      myErrorMap.putValue(repository, remote);
      return result;
    }

    VcsFileUtil.chunkArguments(branchRefNames).forEach(refs -> {
      List<String> params = newArrayList("--heads", remote.getName());
      params.addAll(refs);
      GitCommandResult lsRemoteResult = myGit.runCommand(() -> createLsRemoteHandler(repository, remote, params, authenticationMode));
      if (lsRemoteResult.success()) {
        Map<String, String> hashWithNameMap = map2MapNotNull(lsRemoteResult.getOutput(), GitRefUtil::parseRefsLine);
        result.putAll(getResolvedHashes(hashWithNameMap));
        myErrorMap.remove(repository, remote);
        myAuthSuccessMap.putValue(repository, remote);
      }
      else {
        myErrorMap.putValue(repository, remote);
      }
    });
    return result;
  }

  @NotNull
  private GitLineHandler createLsRemoteHandler(@NotNull GitRepository repository,
                                               @NotNull GitRemote remote,
                                               @NotNull List<String> params, @NotNull GitAuthenticationMode authenticationMode) {
    GitLineHandler h = new GitLineHandler(myProject, repository.getRoot(), GitCommand.LS_REMOTE);
    h.setIgnoreAuthenticationMode(authenticationMode);
    h.addParameters(params);
    h.setUrls(remote.getUrls());
    return h;
  }

  private boolean shouldUpdateBranchesToPull(@NotNull GitRepository repository) {
    if (!shouldCheckIncoming()) return false;
    Map<GitLocalBranch, Hash> cachedBranchesToPull = myLocalBranchesToPull.get(repository);
    return cachedBranchesToPull == null ||
           exists(repository.getBranchTrackInfos(),
                  info -> !Objects.equals(repository.getBranches().getHash(info.getRemoteBranch()),
                                          cachedBranchesToPull.get(info.getLocalBranch())));
  }

  @NotNull
  private Map<GitLocalBranch, Hash> calculateBranchesToPush(@NotNull GitRepository gitRepository) {
    Map<GitLocalBranch, Hash> branchesToPush = new HashMap<>();
    GitBranchesCollection branchesCollection = gitRepository.getBranches();
    for (GitLocalBranch branch : branchesCollection.getLocalBranches()) {
      GitPushTarget pushTarget = GitPushSupport.getPushTargetIfExist(gitRepository, branch);
      Hash localHashForRemoteBranch = pushTarget != null ? branchesCollection.getHash(pushTarget.getBranch()) : null;
      Hash localHash = branchesCollection.getHash(branch);
      if (hasCommitsForBranch(gitRepository, branch, localHash, localHashForRemoteBranch, false)) {
        branchesToPush.put(branch, localHashForRemoteBranch);
      }
    }
    return branchesToPush;
  }

  private boolean hasCommitsForBranch(@NotNull GitRepository repository,
                                      @NotNull GitLocalBranch localBranch,
                                      @Nullable Hash localBranchHash, @Nullable Hash localHashForRemoteBranch,
                                      boolean incoming) {
    if (!supportsIncomingOutgoing()) return false;
    if (localHashForRemoteBranch == null || Objects.equals(localBranchHash, localHashForRemoteBranch)) return false;

    //run git rev-list --count pushTargetForBranch_or_hash..localName for outgoing ( @{push} can be used only for equal branch names)
    //see git-push help -> simple push strategy
    //git rev-list --count localName..localName@{u} for incoming

    GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REV_LIST);
    handler.setSilent(true);
    String branchName = localBranch.getName();
    handler.addParameters("--count", incoming
                                     ? branchName + ".." + branchName + "@{u}"
                                     : localHashForRemoteBranch.asString() + ".." + branchName);
    try {
      String output = myGit.runCommand(handler).getOutputOrThrow().trim();
      return !StringUtil.startsWithChar(output, '0');
    }
    catch (VcsException e) {
      LOG.warn("Can't get outgoing info (git rev-list " + branchName + " failed):" + e.getMessage());
      return false;
    }
  }

  @NotNull
  private static Collection<GitLocalBranch> getBranches(@Nullable GitRepository repository,
                                                        @NotNull Map<GitRepository, Map<GitLocalBranch, Hash>> branchCollection) {
    if (repository != null) {
      Map<GitLocalBranch, Hash> branchHashMap = branchCollection.get(repository);
      return branchHashMap != null ? branchHashMap.keySet() : Collections.emptySet();
    }
    return of(branchCollection.values()).flatMap(StreamEx::ofKeys).collect(toSet());
  }

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    synchronized (LOCK) {
      myDirtyReposPush.add(repository);
      if (shouldUpdateBranchesToPull(repository)) {
        myDirtyReposPull.add(repository);
      }
    }
    scheduleUpdate();
  }

  @Override
  public void authenticationSucceeded(@NotNull GitRepository repository, @NotNull GitRemote remote) {
    if(!shouldCheckIncoming()) return;
    Collection<GitRemote> remotes = myErrorMap.get(repository);
    myAuthSuccessMap.putValue(repository, remote);
    if (remotes.contains(remote)) {
      MultiMap<GitRemote, GitBranchTrackInfo> trackInfoByRemotes = groupTrackInfoByRemotes(repository);
      if (trackInfoByRemotes.containsKey(remote)) {
        final Map<GitLocalBranch, Hash> newBranchMap =
          calcBranchesToPullForRemote(repository, remote, trackInfoByRemotes.get(remote), SILENT);
        myLocalBranchesToPull.compute(repository, (r, branchHashMap) -> {
          if (branchHashMap == null) {
            return new HashMap<>(newBranchMap);
          }
          else {
            branchHashMap.putAll(newBranchMap);
            return branchHashMap;
          }
        });
      }
    }
  }

  @NotNull
  private static MultiMap<GitRemote, GitBranchTrackInfo> groupTrackInfoByRemotes(@NotNull GitRepository repository) {
    return groupBy(repository.getBranchTrackInfos(), GitBranchTrackInfo::getRemote);
  }
}