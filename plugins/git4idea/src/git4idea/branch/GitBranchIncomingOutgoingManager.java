// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.concurrency.JobScheduler;
import com.intellij.externalProcessAuthHelper.AuthenticationMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.update.DisposableUpdate;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import com.intellij.vcs.log.Hash;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.commands.*;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersionSpecialty;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.push.GitPushSupport;
import git4idea.push.GitPushTarget;
import git4idea.repo.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.intellij.externalProcessAuthHelper.AuthenticationMode.SILENT;
import static com.intellij.externalProcessAuthHelper.AuthenticationMode.NONE;
import static git4idea.config.GitIncomingCheckStrategy.Auto;
import static git4idea.config.GitIncomingCheckStrategy.Never;
import static git4idea.repo.GitRefUtil.addRefsHeadsPrefixIfNeeded;
import static git4idea.repo.GitRefUtil.getResolvedHashes;

@Service(Service.Level.PROJECT)
public final class GitBranchIncomingOutgoingManager implements GitRepositoryChangeListener, GitAuthenticationListener, Disposable {
  private static final Logger LOG = Logger.getInstance(GitBranchIncomingOutgoingManager.class);
  public static final Topic<GitIncomingOutgoingListener> GIT_INCOMING_OUTGOING_CHANGED =
    new Topic<>("Git incoming outgoing info changed", GitIncomingOutgoingListener.class);

  private static final String MAC_DEFAULT_LAUNCH = "com.apple.launchd"; //NON-NLS

  private static final boolean HAS_EXTERNAL_SSH_AGENT = hasExternalSSHAgent();

  private final @NotNull Object LOCK = new Object();
  private final @NotNull Set<GitRepository> myDirtyReposWithIncoming = new HashSet<>();
  private final @NotNull Set<GitRepository> myDirtyReposWithOutgoing = new HashSet<>();
  private boolean myShouldRequestRemoteInfo;

  private final @NotNull MergingUpdateQueue myQueue;

  //store map from local branch to related cached remote branch hash per repository
  private final @NotNull Map<GitRepository, Set<GitLocalBranch>> myLocalBranchesWithIncoming = new ConcurrentHashMap<>();
  private final @NotNull Map<GitRepository, Map<GitLocalBranch, Hash>> myLocalBranchesToFetch = new ConcurrentHashMap<>();
  private final @NotNull Map<GitRepository, Set<GitLocalBranch>> myLocalBranchesWithOutgoing = new ConcurrentHashMap<>();
  private final @NotNull MultiMap<GitRepository, GitRemote> myErrorMap = MultiMap.createConcurrentSet();
  private final @NotNull Project myProject;
  private @Nullable ScheduledFuture<?> myPeriodicalUpdater;
  private @Nullable MessageBusConnection myConnection;
  private final @NotNull MultiMap<GitRepository, GitRemote> myAuthSuccessMap = MultiMap.createConcurrentSet();
  private final @NotNull AtomicBoolean myIsUpdating = new AtomicBoolean();

  GitBranchIncomingOutgoingManager(@NotNull Project project) {
    myProject = project;

    myQueue = new MergingUpdateQueue("GitBranchIncomingOutgoingManager", 1000, true, null,
                                     this, null, Alarm.ThreadToUse.POOLED_THREAD);
  }

  @Override
  public void dispose() {
    stopScheduling();
  }

  private static boolean hasExternalSSHAgent() {
    String ssh_auth_sock = EnvironmentUtil.getValue("SSH_AUTH_SOCK");
    if (ssh_auth_sock == null) return false;
    return !StringUtil.contains(ssh_auth_sock, MAC_DEFAULT_LAUNCH);
  }

  public boolean hasIncomingFor(@Nullable GitRepository repository, @NotNull String localBranchName) {
    return shouldCheckIncoming() && getBranchesWithIncoming(repository).contains(new GitLocalBranch(localBranchName));
  }

  public boolean hasOutgoingFor(@Nullable GitRepository repository, @NotNull String localBranchName) {
    return shouldCheckIncomingOutgoing() && getBranchesWithOutgoing(repository).contains(new GitLocalBranch(localBranchName));
  }

  public boolean shouldCheckIncoming() {
    return AdvancedSettings.getBoolean("git.update.incoming.outgoing.info") && GitVcsSettings.getInstance(myProject).getIncomingCheckStrategy() != Never;
  }

  private static boolean shouldCheckIncomingOutgoing() {
    return AdvancedSettings.getBoolean("git.update.incoming.outgoing.info");
  }

  public static @NotNull GitBranchIncomingOutgoingManager getInstance(@NotNull Project project) {
    return project.getService(GitBranchIncomingOutgoingManager.class);
  }

  public boolean supportsIncomingOutgoing() {
    return GitVersionSpecialty.INCOMING_OUTGOING_BRANCH_INFO.existsIn(myProject);
  }

  public void activate() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      if (myConnection == null) {
        myConnection = myProject.getMessageBus().connect(this);
        myConnection.subscribe(GitRepository.GIT_REPO_CHANGE, this);
        myConnection.subscribe(GitAuthenticationListener.GIT_AUTHENTICATION_SUCCESS, this);
      }
      updateBranchesWithOutgoing();
      updateIncomingScheduling();
    });
  }

  public void updateIncomingScheduling() {
    if (myPeriodicalUpdater == null && shouldCheckIncoming()) {
      updateBranchesWithIncoming(true);
      int timeout = Registry.intValue("git.update.incoming.info.time");
      myPeriodicalUpdater = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> updateBranchesWithIncoming(true), timeout, timeout,
                                                                               TimeUnit.MINUTES);
    }
    else if (myPeriodicalUpdater != null && !shouldCheckIncoming()) {
      stopScheduling();
    }
  }

  @RequiresEdt
  private void stopScheduling() {
    if (myPeriodicalUpdater != null) {
      myPeriodicalUpdater.cancel(true);
      myPeriodicalUpdater = null;
    }
  }

  @CalledInAny
  public void forceUpdateBranches(@Nullable Runnable runAfterUpdate) {
    if (!myIsUpdating.compareAndSet(false, true)) return;
    updateBranchesWithIncoming(false);
    updateBranchesWithOutgoing();
    new Task.Backgroundable(myProject, GitBundle.message("branches.update.info.process")) {
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

      @Override
      public void onFinished() {
        myIsUpdating.set(false);
        if (runAfterUpdate != null) {
          runAfterUpdate.run();
        }
      }
    }.queue();
  }

  public boolean isUpdating() {
    return myIsUpdating.get();
  }

  private void scheduleUpdate() {
    myQueue.queue(DisposableUpdate.createDisposable(this, "update", () -> {
      List<GitRepository> withIncoming;
      List<GitRepository> withOutgoing;

      boolean shouldRequestRemoteInfo;
      synchronized (LOCK) {
        withIncoming = new ArrayList<>(myDirtyReposWithIncoming);
        withOutgoing = new ArrayList<>(myDirtyReposWithOutgoing);
        shouldRequestRemoteInfo = myShouldRequestRemoteInfo;

        myDirtyReposWithIncoming.clear();
        myDirtyReposWithOutgoing.clear();
        myShouldRequestRemoteInfo = false;
      }

      for (GitRepository r : withOutgoing) {
        myLocalBranchesWithOutgoing.put(r, calculateBranchesWithOutgoing(r));
      }
      for (GitRepository r : withIncoming) {
        if (shouldRequestRemoteInfo) {
          myLocalBranchesToFetch.put(r, calculateBranchesToFetch(r));
        }
        myLocalBranchesWithIncoming.put(r, calcBranchesWithIncoming(r));
      }
      BackgroundTaskUtil.syncPublisher(myProject, GIT_INCOMING_OUTGOING_CHANGED).incomingOutgoingInfoChanged();
    }));
  }

  public @NotNull Collection<GitLocalBranch> getBranchesWithIncoming(@Nullable GitRepository repository) {
    return getBranches(repository, myLocalBranchesWithIncoming);
  }

  public @NotNull Collection<GitLocalBranch> getBranchesWithOutgoing(@Nullable GitRepository repository) {
    return getBranches(repository, myLocalBranchesWithOutgoing);
  }

  private void updateBranchesWithIncoming(boolean fromRemote) {
    if (!shouldCheckIncoming()) return;
    synchronized (LOCK) {
      myShouldRequestRemoteInfo = fromRemote;
      myDirtyReposWithIncoming.addAll(GitRepositoryManager.getInstance(myProject).getRepositories());
    }
    scheduleUpdate();
  }

  private void updateBranchesWithOutgoing() {
    if(!shouldCheckIncomingOutgoing()) return;
    synchronized (LOCK) {
      myDirtyReposWithOutgoing.addAll(GitRepositoryManager.getInstance(myProject).getRepositories());
    }
    scheduleUpdate();
  }

  private @NotNull Map<GitLocalBranch, Hash> calculateBranchesToFetch(@NotNull GitRepository repository) {
    Map<GitLocalBranch, Hash> result = new HashMap<>();
    groupTrackInfoByRemotes(repository).entrySet()
      .forEach(entry -> result.putAll(calcBranchesToFetchForRemote(repository, entry.getKey(), entry.getValue(),
                                                                   getAuthenticationMode(repository, entry.getKey()
                                                                   ))));
    return result;
  }

  private @NotNull Map<GitLocalBranch, Hash> calcBranchesToFetchForRemote(@NotNull GitRepository repository,
                                                                          @NotNull GitRemote gitRemote,
                                                                          @NotNull Collection<? extends GitBranchTrackInfo> trackInfoList,
                                                                          AuthenticationMode mode) {
    Map<GitLocalBranch, Hash> result = new HashMap<>();
    GitBranchesCollection branchesCollection = repository.getBranches();
    final Map<String, Hash> remoteNameWithHash =
      lsRemote(repository, gitRemote, ContainerUtil.map(trackInfoList, info -> info.getRemoteBranch().getNameForRemoteOperations()), mode);

    for (Map.Entry<String, Hash> hashEntry : remoteNameWithHash.entrySet()) {
      String remoteBranchName = hashEntry.getKey();
      Hash remoteHash = hashEntry.getValue();
      trackInfoList.forEach(info -> {
        GitRemoteBranch remoteBranch = info.getRemoteBranch();
        Hash localHashForRemoteBranch = branchesCollection.getHash(remoteBranch);

        if (localHashForRemoteBranch == null) return;

        if (StringUtil.equals(remoteBranchName, addRefsHeadsPrefixIfNeeded(remoteBranch.getNameForRemoteOperations()))) {
          if (!localHashForRemoteBranch.equals(remoteHash)) {
            result.put(info.getLocalBranch(), localHashForRemoteBranch);
          }
        }
      });
    }
    return result;
  }

  private @NotNull Set<GitLocalBranch> calcBranchesWithIncoming(@NotNull GitRepository repository) {

    Set<GitLocalBranch> result = new HashSet<>();
    GitBranchesCollection branchesCollection = repository.getBranches();
    Map<GitLocalBranch, Hash> cachedBranchesToFetch = myLocalBranchesToFetch.get(repository);

    branchesCollection.getLocalBranches().forEach(localBranch -> {
      GitBranchTrackInfo info = GitBranchUtil.getTrackInfoForBranch(repository, localBranch);
      if (info == null) return;
      Hash localHashForRemoteBranch = branchesCollection.getHash(info.getRemoteBranch());
      Hash localHash = branchesCollection.getHash(localBranch);
      if (localHashForRemoteBranch == null) return;

      if (hasCommitsForBranch(repository, info.getLocalBranch(), localHash, localHashForRemoteBranch, true)) {
        result.add(info.getLocalBranch());
      }
      else if (cachedBranchesToFetch != null && localHashForRemoteBranch.equals(cachedBranchesToFetch.get(localBranch))) {
        result.add(info.getLocalBranch());
      }
    });
    return result;
  }

  private @NotNull AuthenticationMode getAuthenticationMode(@NotNull GitRepository repository,
                                                            @NotNull GitRemote remote) {
    return (myAuthSuccessMap.get(repository).contains(remote)) ? SILENT : NONE;
  }

  private boolean shouldAvoidUserInteraction(@NotNull GitRemote remote) {
    return GitVcsSettings.getInstance(myProject).getIncomingCheckStrategy() == Auto && HAS_EXTERNAL_SSH_AGENT && containsSSHUrl(remote);
  }

  private static boolean containsSSHUrl(@NotNull GitRemote remote) {
    return ContainerUtil.exists(remote.getUrls(), url -> !url.startsWith(URLUtil.HTTP_PROTOCOL));
  }

  private @NotNull Map<String, Hash> lsRemote(@NotNull GitRepository repository,
                                              @NotNull GitRemote remote,
                                              @NotNull List<String> branchRefNames,
                                              @NotNull AuthenticationMode authenticationMode) {
    Map<String, Hash> result = new HashMap<>();

    if (!supportsIncomingOutgoing()) return result;
    if (authenticationMode == NONE || (authenticationMode == SILENT && shouldAvoidUserInteraction(remote))) {
      myErrorMap.putValue(repository, remote);
      return result;
    }

    VcsFileUtil.chunkArguments(branchRefNames).forEach(refs -> {
      List<String> params = ContainerUtil.newArrayList("--heads", remote.getName()); //NON-NLS
      params.addAll(refs);
      GitCommandResult lsRemoteResult =
        Git.getInstance().runCommand(() -> createLsRemoteHandler(repository, remote, params, authenticationMode));
      if (lsRemoteResult.success()) {
        Map<String, String> hashWithNameMap = ContainerUtil.map2MapNotNull(lsRemoteResult.getOutput(), GitRefUtil::parseRefsLine);
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

  private @NotNull GitLineHandler createLsRemoteHandler(@NotNull GitRepository repository,
                                                        @NotNull GitRemote remote,
                                                        @NotNull List<String> params, @NotNull AuthenticationMode authenticationMode) {
    GitLineHandler h = new GitLineHandler(myProject, repository.getRoot(), GitCommand.LS_REMOTE);
    h.setIgnoreAuthenticationMode(authenticationMode);
    h.addParameters(params);
    h.setUrls(remote.getUrls());
    return h;
  }

  private @NotNull Set<GitLocalBranch> calculateBranchesWithOutgoing(@NotNull GitRepository gitRepository) {
    Set<GitLocalBranch> branchesWithOutgoing = new HashSet<>();
    GitBranchesCollection branchesCollection = gitRepository.getBranches();
    for (GitLocalBranch branch : branchesCollection.getLocalBranches()) {
      GitPushTarget pushTarget = GitPushSupport.getPushTargetIfExist(gitRepository, branch);
      Hash localHashForRemoteBranch = pushTarget != null ? branchesCollection.getHash(pushTarget.getBranch()) : null;
      Hash localHash = branchesCollection.getHash(branch);
      if (hasCommitsForBranch(gitRepository, branch, localHash, localHashForRemoteBranch, false)) {
        branchesWithOutgoing.add(branch);
      }
    }
    return branchesWithOutgoing;
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
    String branchName = localBranch.getName();
    String from = incoming ? branchName : localHashForRemoteBranch.asString();
    String to = incoming ? branchName + "@{u}" : branchName;
    String numberOfCommitsBetween = GitHistoryUtils.getNumberOfCommitsBetween(repository, from, to);
    if (numberOfCommitsBetween == null) {
      LOG.warn("Can't get outgoing info (git rev-list " + branchName + " failed)");
      return false;
    }
    return !StringUtil.startsWithChar(numberOfCommitsBetween, '0');
  }

  private static @NotNull Collection<GitLocalBranch> getBranches(@Nullable GitRepository repository,
                                                                 @NotNull Map<GitRepository, Set<GitLocalBranch>> branchCollection) {
    if (repository != null) {
      return Objects.requireNonNullElse(branchCollection.get(repository), Collections.emptySet());
    }
    return StreamEx.of(branchCollection.values()).flatMap(Set::stream).collect(Collectors.toSet());
  }

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    if (!shouldCheckIncomingOutgoing()) return;
    synchronized (LOCK) {
      myDirtyReposWithOutgoing.add(repository);
      myDirtyReposWithIncoming.add(repository);
    }
    scheduleUpdate();
  }

  @Override
  public void authenticationSucceeded(@NotNull GitRepository repository, @NotNull GitRemote remote) {
    if (!shouldCheckIncoming()) return;
    myAuthSuccessMap.putValue(repository, remote);
  }

  private static @NotNull MultiMap<GitRemote, GitBranchTrackInfo> groupTrackInfoByRemotes(@NotNull GitRepository repository) {
    return ContainerUtil.groupBy(repository.getBranchTrackInfos(), GitBranchTrackInfo::getRemote);
  }

  public interface GitIncomingOutgoingListener {
    void incomingOutgoingInfoChanged();
  }
}