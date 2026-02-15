// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.concurrency.JobScheduler;
import com.intellij.dvcs.repo.RepositoryExtKt;
import com.intellij.externalProcessAuthHelper.AuthenticationMode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.vcs.impl.shared.RepositoryId;
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
import com.intellij.vcs.git.branch.GitInOutCountersInProject;
import com.intellij.vcs.git.branch.GitInOutCountersInRepo;
import com.intellij.vcs.git.branch.GitInOutProjectState;
import com.intellij.vcs.log.Hash;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitLocalBranch;
import git4idea.GitOperationsCollector;
import git4idea.GitRemoteBranch;
import git4idea.commands.Git;
import git4idea.commands.GitAuthenticationListener;
import git4idea.commands.GitCommand;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitIncomingRemoteCheckStrategy;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersionSpecialty;
import git4idea.fetch.GitFetchHandler;
import git4idea.fetch.GitFetchSpec;
import git4idea.fetch.GitFetchSupport;
import git4idea.history.GitHistoryUtils;
import git4idea.push.GitPushSupport;
import git4idea.push.GitPushTarget;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRefUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import kotlin.text.StringsKt;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.intellij.externalProcessAuthHelper.AuthenticationMode.NONE;
import static com.intellij.externalProcessAuthHelper.AuthenticationMode.SILENT;
import static git4idea.repo.GitRefUtil.addRefsHeadsPrefixIfNeeded;
import static git4idea.repo.GitRefUtil.getResolvedHashes;

@Service(Service.Level.PROJECT)
public final class GitBranchIncomingOutgoingManager implements GitRepositoryChangeListener, GitAuthenticationListener, Disposable {

  private static final Logger LOG = Logger.getInstance(GitBranchIncomingOutgoingManager.class);

  @Topic.ProjectLevel
  public static final Topic<GitIncomingOutgoingListener> GIT_INCOMING_OUTGOING_CHANGED =
    new Topic<>("Git incoming outgoing info changed", GitIncomingOutgoingListener.class);

  private static final String MAC_DEFAULT_LAUNCH = "com.apple.launchd"; //NON-NLS

  private static final NotNullLazyValue<Boolean> HAS_EXTERNAL_SSH_AGENT = NotNullLazyValue.lazy(() -> hasExternalSSHAgent());

  private final @NotNull Object LOCK = new Object();
  private final @NotNull Set<GitRepository> myDirtyReposWithIncoming = new HashSet<>();
  private final @NotNull Set<GitRepository> myDirtyReposWithOutgoing = new HashSet<>();
  private boolean myShouldRequestRemoteInfo;

  private final @NotNull MergingUpdateQueue myQueue;

  //store map from local branch to related cached remote branch hash per repository
  private final @NotNull Map<GitRepository, Map<GitLocalBranch, Integer>> myLocalBranchesWithIncoming = new ConcurrentHashMap<>();
  private final @NotNull Map<GitRepository, Map<GitLocalBranch, Hash>> myLocalBranchesToFetch = new ConcurrentHashMap<>();
  private final @NotNull Map<GitRepository, Map<GitLocalBranch, Integer>> myLocalBranchesWithOutgoing = new ConcurrentHashMap<>();
  private final @NotNull Project myProject;
  private @Nullable ScheduledFuture<?> myPeriodicalUpdater;
  private @Nullable MessageBusConnection myConnection;
  private final @NotNull MultiMap<GitRepository, GitRemote> myAuthSuccessMap = MultiMap.createConcurrentSet();

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

    if (StringUtil.contains(ssh_auth_sock, MAC_DEFAULT_LAUNCH)) {
      try {
        Path agentPath = Path.of(ssh_auth_sock);
        String originPath = agentPath.toString();
        String realPath = agentPath.toRealPath().toString();
        if (!originPath.equals(realPath)) {
          return true;
        }
      }
      catch (Throwable ignored) {
      }

      return false;
    }

    return true;
  }

  public boolean hasIncomingFor(@Nullable GitRepository repository, @NotNull String localBranchName) {
    return getBranchesWithIncoming(repository).contains(new GitLocalBranch(localBranchName));
  }

  public boolean hasOutgoingFor(@Nullable GitRepository repository, @NotNull String localBranchName) {
    return getBranchesWithOutgoing(repository).contains(new GitLocalBranch(localBranchName));
  }

  /**
   * @return the number of incoming commits for the specified branch if present.
   * If there are no incoming commits, but it is known that something is non-fetched from a remote,
   */
  private @Nullable Integer getIncomingFor(@Nullable GitRepository repository, @NotNull GitLocalBranch localBranch) {
    if (!shouldShow()) return null;
    Map<GitLocalBranch, Integer> incomingForRepo = myLocalBranchesWithIncoming.get(repository);
    if (incomingForRepo == null) return null;

    return incomingForRepo.get(localBranch);
  }

  private @Nullable Integer getOutgoingFor(@Nullable GitRepository repository, @NotNull GitLocalBranch localBranch) {
    if (!shouldShow()) return null;
    Map<GitLocalBranch, Integer> outgoingForRepo = myLocalBranchesWithOutgoing.get(repository);
    if (outgoingForRepo == null) return null;
    return outgoingForRepo.get(localBranch);
  }

  @ApiStatus.Internal
  public @NotNull GitInOutCountersInProject getIncomingOutgoingState(@NotNull GitRepository repository,
                                                                     @NotNull GitLocalBranch localBranch) {
    return getIncomingOutgoingState(Collections.singleton(repository), localBranch);
  }

  @ApiStatus.Internal
  public @NotNull GitInOutCountersInProject getIncomingOutgoingState(@NotNull Collection<GitRepository> repositories,
                                                                     @NotNull GitLocalBranch localBranch) {
    if (!shouldShow() || repositories.isEmpty()) return GitInOutCountersInProject.EMPTY;

    Map<RepositoryId, GitInOutCountersInRepo> repoStates = repositories.stream()
      .map(r -> {
        Integer incoming = getIncomingFor(r, localBranch);
        Integer outgoing = getOutgoingFor(r, localBranch);
        if (incoming == null && outgoing == null) return null;

        return new Pair<>(RepositoryExtKt.repositoryId(r), new GitInOutCountersInRepo(incoming, outgoing));
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toMap(pair -> pair.first, pair -> pair.second));

    if (repoStates.isEmpty()) return GitInOutCountersInProject.EMPTY;

    return new GitInOutCountersInProject(repoStates);
  }

  private @NotNull GitIncomingRemoteCheckStrategy getIncomingRemoteCheckStrategy() {
    return !shouldShow() ? GitIncomingRemoteCheckStrategy.NONE : GitVcsSettings.getInstance(myProject).getIncomingCommitsCheckStrategy();
  }

  private static boolean shouldShow() {
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
        myConnection.subscribe(GIT_AUTHENTICATION_SUCCESS, this);
        myConnection.subscribe(GitVcsSettings.GitVcsSettingsListener.TOPIC, new GitVcsSettings.GitVcsSettingsListener() {
          @Override
          public void incomingCommitsCheckStrategyChanged(@NotNull GitIncomingRemoteCheckStrategy strategy) {
            ApplicationManager.getApplication().invokeLater(() -> updateIncomingScheduling());
          }
        });
      }
      updateAllBranchesWithOutgoing();
      updateIncomingScheduling();
    });
  }

  private void updateIncomingScheduling() {
    boolean shouldCheckIncomingOnRemote = getIncomingRemoteCheckStrategy() != GitIncomingRemoteCheckStrategy.NONE;
    if (myPeriodicalUpdater == null && shouldCheckIncomingOnRemote) {
      updateAllBranchesWithIncomingFromRemote();
      int timeout = Registry.intValue("git.update.incoming.info.time");
      myPeriodicalUpdater =
        JobScheduler.getScheduler().scheduleWithFixedDelay(() -> updateAllBranchesWithIncomingFromRemote(), timeout, timeout,
                                                           TimeUnit.MINUTES);
    }
    else if (myPeriodicalUpdater != null && !shouldCheckIncomingOnRemote) {
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

  private void scheduleUpdate() {
    myQueue.queue(DisposableUpdate.createDisposable(this, "update", this::runUpdate));
  }

  private void runUpdate() {
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

    if (shouldRequestRemoteInfo) {
      GitIncomingRemoteCheckStrategy remoteCheckStrategy = getIncomingRemoteCheckStrategy();
      requestRemoteInfo(remoteCheckStrategy, withIncoming);
    }
    else {
      LOG.debug("No remote state refresh requested");
    }

    for (GitRepository r : withIncoming) {
      myLocalBranchesWithIncoming.put(r, calcBranchesWithIncoming(r));
    }
    BackgroundTaskUtil.syncPublisher(myProject, GIT_INCOMING_OUTGOING_CHANGED).incomingOutgoingInfoChanged();
  }

  private void requestRemoteInfo(GitIncomingRemoteCheckStrategy remoteCheckStrategy, List<GitRepository> repositories) {
    myLocalBranchesToFetch.remove(repositories);
    if (remoteCheckStrategy == GitIncomingRemoteCheckStrategy.NONE) {
      LOG.debug("Remote check disabled");
      return;
    }

    AtomicBoolean success = new AtomicBoolean(false);
    try {
      switch (remoteCheckStrategy) {
        case FETCH -> {
          LOG.info("Fetching %d repositories".formatted(repositories.size()));
          List<GitFetchSpec> remotesToFetch = new ArrayList<>();
          for (GitRepository repository : repositories) {
            for (GitRemote remote : repository.getRemotes()) {
              remotesToFetch.add(new GitFetchSpec(repository, remote, getAuthenticationMode(repository, remote)));
            }
          }
          success.set(GitFetchSupport.fetchSupport(myProject).fetch(remotesToFetch).isSuccessful());
        }
        case LS_REMOTE -> {
          LOG.info("Listing remote info for %d repositories".formatted(repositories.size()));
          repositories.forEach(r -> myLocalBranchesToFetch.put(r, calculateBranchesToFetch(r, () -> success.set(false))));
        }
      }
    }
    finally {
      GitOperationsCollector.logRemoteInfoRequest(myProject, remoteCheckStrategy, success.get());
    }
  }

  @ApiStatus.Internal
  public @NotNull GitInOutProjectState getState() {
    return !shouldShow()
           ? GitInOutProjectState.EMPTY
           : new GitInOutProjectState(mapState(myLocalBranchesWithIncoming), mapState(myLocalBranchesWithOutgoing));
  }

  private static @NotNull Map<RepositoryId, Map<String, Integer>> mapState(@NotNull Map<GitRepository, Map<GitLocalBranch, Integer>> projectState) {
    var result = new HashMap<RepositoryId, Map<String, Integer>>();
    projectState.forEach((repo, branches) -> result.put(RepositoryExtKt.repositoryId(repo), remapLocalBranchToName(branches)));
    return result;
  }

  private static @NotNull Map<String, Integer> remapLocalBranchToName(@NotNull Map<GitLocalBranch, Integer> repoState) {
    Map<String, Integer> result = new HashMap<>();
    repoState.forEach((localBranch, commits) -> result.put(localBranch.getName(), commits));
    return result;
  }

  public @NotNull Collection<GitLocalBranch> getBranchesWithIncoming(@Nullable GitRepository repository) {
    return getBranches(repository, myLocalBranchesWithIncoming);
  }

  public @NotNull Collection<GitLocalBranch> getBranchesWithOutgoing(@Nullable GitRepository repository) {
    return getBranches(repository, myLocalBranchesWithOutgoing);
  }

  private void updateAllBranchesWithIncomingFromRemote() {
    markDirty(GitRepositoryManager.getInstance(myProject).getRepositories(), null, true);
  }

  private void updateAllBranchesWithOutgoing() {
    markDirty(null, GitRepositoryManager.getInstance(myProject).getRepositories(), false);
  }

  private void markDirty(@Nullable Collection<GitRepository> withIncoming,
                         @Nullable Collection<GitRepository> withOutgoing,
                         boolean requestRemoteInfo) {
    synchronized (LOCK) {
      if (requestRemoteInfo) {
        myShouldRequestRemoteInfo = true;
      }
      if (withIncoming != null) {
        myDirtyReposWithIncoming.addAll(withIncoming);
      }
      if (withOutgoing != null) {
        myDirtyReposWithOutgoing.addAll(withOutgoing);
      }
    }
    scheduleUpdate();
  }

  private @NotNull Map<GitLocalBranch, Hash> calculateBranchesToFetch(@NotNull GitRepository repository,
                                                                      @NotNull Runnable onError) {
    Map<GitLocalBranch, Hash> result = new HashMap<>();
    groupTrackInfoByRemotes(repository).entrySet()
      .forEach(entry -> result.putAll(calcBranchesToFetchForRemote(repository, entry.getKey(), entry.getValue(), onError)));
    return result;
  }

  private @NotNull Map<GitLocalBranch, Hash> calcBranchesToFetchForRemote(@NotNull GitRepository repository,
                                                                          @NotNull GitRemote gitRemote,
                                                                          @NotNull Collection<? extends GitBranchTrackInfo> trackInfoList,
                                                                          @NotNull Runnable onError) {
    Map<GitLocalBranch, Hash> result = new HashMap<>();
    GitBranchesCollection branchesCollection = repository.getBranches();
    final Map<String, Hash> remoteNameWithHash =
      lsRemote(repository, gitRemote, ContainerUtil.map(trackInfoList, info -> info.getRemoteBranch().getNameForRemoteOperations()), onError);

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

  private @NotNull Map<GitLocalBranch, Integer> calcBranchesWithIncoming(@NotNull GitRepository repository) {
    Map<GitLocalBranch, Integer> result = new HashMap<>();
    GitBranchesCollection branchesCollection = repository.getBranches();
    Map<GitLocalBranch, Hash> cachedBranchesToFetch = myLocalBranchesToFetch.get(repository);

    branchesCollection.getLocalBranches().forEach(localBranch -> {
      GitBranchTrackInfo info = GitBranchUtil.getTrackInfoForBranch(repository, localBranch);
      if (info == null) return;
      Hash localHashForRemoteBranch = branchesCollection.getHash(info.getRemoteBranch());
      Hash localHash = branchesCollection.getHash(localBranch);
      if (localHashForRemoteBranch == null) return;

      Integer commits = getCommitsForBranch(repository, info.getLocalBranch(), localHash, localHashForRemoteBranch, true);
      if (commits != null && commits > 0) {
        result.put(info.getLocalBranch(), commits);
      }
      else if (cachedBranchesToFetch != null && localHashForRemoteBranch.equals(cachedBranchesToFetch.get(localBranch))) {
        // In this case, we know that the remote branch has some new commits,
        // but they aren't fetched yet, so we can't count them
        // TODO: fetch?
        result.put(info.getLocalBranch(), 0);
      }
    });
    return result;
  }

  /**
   * {@link AuthenticationMode#NONE} is used until the first successful authentication on the remote to reduce risk
   * of showing OS password storage dialog.
   *
   * @see AuthenticationMode
   */
  private @NotNull AuthenticationMode getAuthenticationMode(@NotNull GitRepository repository,
                                                            @NotNull GitRemote remote) {
    return myAuthSuccessMap.get(repository).contains(remote) ? SILENT : NONE;
  }

  private static boolean shouldAvoidUserInteraction(@NotNull GitRemote remote) {
    return containsSSHUrl(remote) && HAS_EXTERNAL_SSH_AGENT.get();
  }

  private static boolean containsSSHUrl(@NotNull GitRemote remote) {
    return ContainerUtil.exists(remote.getUrls(), url -> !url.startsWith(URLUtil.HTTP_PROTOCOL));
  }

  private @NotNull Map<String, Hash> lsRemote(@NotNull GitRepository repository,
                                              @NotNull GitRemote remote,
                                              @NotNull List<String> branchRefNames,
                                              @NotNull Runnable onError) {
    Map<String, Hash> result = new HashMap<>();

    if (!supportsIncomingOutgoing()) return result;
    if (shouldAvoidUserInteraction(remote)) {
      return result;
    }

    VcsFileUtil.chunkArguments(branchRefNames).forEach(refs -> {
      List<String> params = ContainerUtil.concat(List.of("--heads", remote.getName()), refs);
      GitCommandResult lsRemoteResult =
        Git.getInstance().runCommand(() -> createLsRemoteHandler(repository, remote, params));
      if (lsRemoteResult.success()) {
        Map<String, String> hashWithNameMap = ContainerUtil.map2MapNotNull(lsRemoteResult.getOutput(), GitRefUtil::parseBranchesLine);
        result.putAll(getResolvedHashes(hashWithNameMap));
        myAuthSuccessMap.putValue(repository, remote);
      }
      else {
        onError.run();
      }
    });
    return result;
  }

  private @NotNull GitLineHandler createLsRemoteHandler(@NotNull GitRepository repository,
                                                        @NotNull GitRemote remote,
                                                        @NotNull List<String> params) {
    GitLineHandler h = new GitLineHandler(myProject, repository.getRoot(), GitCommand.LS_REMOTE);
    h.setIgnoreAuthenticationMode(getAuthenticationMode(repository, remote));
    h.addParameters(params);
    h.setUrls(remote.getUrls());
    return h;
  }

  private @NotNull Map<GitLocalBranch, Integer> calculateBranchesWithOutgoing(@NotNull GitRepository gitRepository) {
    Map<GitLocalBranch, Integer> branchesWithOutgoing = new HashMap<>();
    GitBranchesCollection branchesCollection = gitRepository.getBranches();
    for (GitLocalBranch branch : branchesCollection.getLocalBranches()) {
      GitPushTarget pushTarget = GitPushSupport.getPushTargetIfExist(gitRepository, branch);
      Hash localHashForRemoteBranch = pushTarget != null ? branchesCollection.getHash(pushTarget.getBranch()) : null;
      Hash localHash = branchesCollection.getHash(branch);
      Integer commits = getCommitsForBranch(gitRepository, branch, localHash, localHashForRemoteBranch, false);
      if (commits != null && commits > 0) {
        branchesWithOutgoing.put(branch, commits);
      }
    }
    return branchesWithOutgoing;
  }

  private Integer getCommitsForBranch(@NotNull GitRepository repository,
                                      @NotNull GitLocalBranch localBranch,
                                      @Nullable Hash localBranchHash, @Nullable Hash localHashForRemoteBranch,
                                      boolean incoming) {
    if (!supportsIncomingOutgoing()) return null;
    if (localHashForRemoteBranch == null || Objects.equals(localBranchHash, localHashForRemoteBranch)) return null;

    //run git rev-list --count pushTargetForBranch_or_hash..localName for outgoing ( @{push} can be used only for equal branch names)
    //see git-push help -> simple push strategy
    //git rev-list --count localName..localName@{u} for incoming
    String branchName = localBranch.getName();
    String from = incoming ? branchName : localHashForRemoteBranch.asString();
    String to = incoming ? branchName + "@{u}" : branchName;
    String numberOfCommitsBetween = GitHistoryUtils.getNumberOfCommitsBetween(repository, from, to);
    if (numberOfCommitsBetween == null) {
      LOG.warn("Can't get outgoing info (git rev-list " + branchName + " failed)");
      return null;
    }
    return StringsKt.toIntOrNull(numberOfCommitsBetween);
  }

  private static @NotNull Collection<GitLocalBranch> getBranches(@Nullable GitRepository repository,
                                                                 @NotNull Map<GitRepository, Map<GitLocalBranch, Integer>> branchCollection) {
    if (!shouldShow()) return Collections.emptySet();

    if (repository != null) {
      Map<GitLocalBranch, Integer> map = Objects.requireNonNullElse(branchCollection.get(repository), Collections.emptyMap());
      return map.keySet();
    }
    return StreamEx.of(branchCollection.values()).map(map -> map.keySet()).nonNull().flatMap(branches -> branches.stream())
      .collect(Collectors.toSet());
  }

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    Collection<GitRepository> repos = Collections.singleton(repository);
    markDirty(repos, repos, false);
  }

  @Override
  public void authenticationSucceeded(@NotNull GitRepository repository, @NotNull GitRemote remote) {
    if (getIncomingRemoteCheckStrategy() == GitIncomingRemoteCheckStrategy.NONE) return;
    myAuthSuccessMap.putValue(repository, remote);
  }

  private static @NotNull MultiMap<GitRemote, GitBranchTrackInfo> groupTrackInfoByRemotes(@NotNull GitRepository repository) {
    return ContainerUtil.groupBy(repository.getBranchTrackInfos(), GitBranchTrackInfo::getRemote);
  }

  @TestOnly
  @ApiStatus.Internal
  public void updateForTests() {
    List<GitRepository> repositories = GitRepositoryManager.getInstance(myProject).getRepositories();
    myDirtyReposWithIncoming.addAll(repositories);
    myDirtyReposWithOutgoing.addAll(repositories);
    myShouldRequestRemoteInfo = true;
    runUpdate();
  }

  public interface GitIncomingOutgoingListener {
    void incomingOutgoingInfoChanged();
  }

  static final class IncomingOutgoingRefreshFetchHandler implements GitFetchHandler {
    @Override
    public void doAfterSuccessfulFetch(@NotNull Project project,
                                       @NotNull Map<GitRepository, ? extends List<GitRemote>> fetches,
                                       @NotNull ProgressIndicator indicator) {
      Set<GitRepository> updatedRepos = fetches.keySet();
      if (updatedRepos.isEmpty()) return;

      getInstance(project).markDirty(updatedRepos, updatedRepos, false);
    }
  }
}