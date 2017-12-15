// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.Hash;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitLocalBranch;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.config.GitVcsSettings;
import git4idea.push.GitPushSupport;
import git4idea.push.GitPushTarget;
import git4idea.repo.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.intellij.util.containers.ContainerUtil.*;
import static git4idea.repo.GitRefUtil.addRefsHeadsPrefixIfNeeded;
import static git4idea.repo.GitRefUtil.getResolvedHashes;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;
import static one.util.streamex.StreamEx.of;

public class GitBranchIncomingOutgoingManager implements GitRepositoryChangeListener, GitAuthenticationListener {

  //store map from local branch to related cached remote branch hash per repository
  @NotNull private final Map<GitRepository, Map<GitLocalBranch, Hash>> myLocalBranchesToPull = newConcurrentMap();
  @NotNull private final Map<GitRepository, Map<GitLocalBranch, Hash>> myLocalBranchesToPush = newConcurrentMap();
  @NotNull private final Map<GitRepository, Set<GitRemote>> myAuthErrorMap = newConcurrentMap();
  @NotNull private final Project myProject;
  @Nullable private ScheduledFuture<?> myPeriodicalUpdater;
  private volatile boolean myUseForceAuthentication;
  @NotNull private final GitRepositoryManager myRepositoryManager;

  GitBranchIncomingOutgoingManager(@NotNull Project project, @NotNull GitRepositoryManager repositoryManager) {
    myProject = project;
    myRepositoryManager = repositoryManager;
  }

  @NotNull
  public static GitBranchIncomingOutgoingManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitBranchIncomingOutgoingManager.class);
  }

  public boolean hasAuthenticationProblems() {
    return !myAuthErrorMap.isEmpty();
  }

  public void setUseForceAuthentication(boolean useForceAuthentication) {
    myUseForceAuthentication = useForceAuthentication;
  }

  @CalledInAwt
  public void startScheduling() {
    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, this);
    connection.subscribe(GitLineHandler.GIT_AUTHENTICATION_SUCCESS, this);
    forceUpdateBranches();
    if (myPeriodicalUpdater == null && !myProject.isDisposed()) {
      int updateTime = GitVcsSettings.getInstance(myProject).getBranchInfoUpdateTime();
      myPeriodicalUpdater = JobScheduler.getScheduler().scheduleWithFixedDelay(() -> updateBranchesToPull(), updateTime, updateTime,
                                                                               TimeUnit.MINUTES);
      Disposer.register(myProject, new Disposable() {
        @Override
        public void dispose() {
          stopScheduling();
        }
      });
    }
  }

  @CalledInAwt
  public void stopScheduling() {
    if (myPeriodicalUpdater != null) {
      myPeriodicalUpdater.cancel(true);
      myPeriodicalUpdater = null;
    }
    myProject.getMessageBus().connect().disconnect();
  }

  @CalledInAny
  public void forceUpdateBranches() {
    GitVcs.runInBackground(new Task.Backgroundable(myProject, "Update Branches Info...", true) {
                             @Override
                             public void run(@NotNull ProgressIndicator indicator) {
                               List<GitRepository> gitRepositories = myRepositoryManager.getRepositories();
                               gitRepositories.forEach(r -> myLocalBranchesToPush.put(r, calculateBranchesToPush(r)));
                               gitRepositories.forEach(r -> myLocalBranchesToPull.put(r, calculateBranchesToPull(r)));
                             }
                           }
    );
  }

  @NotNull
  public Collection<GitLocalBranch> getBranchesToPull(@Nullable GitRepository repository) {
    return getBranches(repository, myLocalBranchesToPull);
  }

  @NotNull
  public Collection<GitLocalBranch> getBranchesToPush(@Nullable GitRepository repository) {
    return getBranches(repository, myLocalBranchesToPush);
  }

  @CalledInBackground
  private void updateBranchesToPull() {
    myRepositoryManager.getRepositories().forEach(r -> myLocalBranchesToPull.put(r, calculateBranchesToPull(r)));
  }

  @NotNull
  private Map<GitLocalBranch, Hash> calculateBranchesToPull(@NotNull GitRepository repository) {
    Map<GitLocalBranch, Hash> result = newHashMap();
    Map<GitRemote, List<GitBranchTrackInfo>> trackInfoByRemotes = groupTrackInfoByRemotes(repository);
    for (Map.Entry<GitRemote, List<GitBranchTrackInfo>> entry : trackInfoByRemotes.entrySet()) {
      result.putAll(calcBranchesToPullForRemote(repository, entry.getKey(), entry.getValue()));
    }
    return result;
  }

  @NotNull
  private Map<GitLocalBranch, Hash> calcBranchesToPullForRemote(@NotNull GitRepository repository,
                                                                @NotNull GitRemote gitRemote,
                                                                @NotNull List<GitBranchTrackInfo> trackInfoList) {
    Map<GitLocalBranch, Hash> result = newHashMap();
    GitBranchesCollection branchesCollection = repository.getBranches();
    final Map<String, Hash> remoteNameWithHash =
      lsRemote(repository, gitRemote, map(trackInfoList, info -> info.getRemoteBranch().getNameForRemoteOperations()));

    for (Map.Entry<String, Hash> hashEntry : remoteNameWithHash.entrySet()) {
      String remoteBranchName = hashEntry.getKey();
      Hash remoteHash = hashEntry.getValue();
      of(trackInfoList)
        .filter(
          info -> StringUtil.equals(remoteBranchName, addRefsHeadsPrefixIfNeeded(info.getRemoteBranch().getNameForRemoteOperations())))
        .filter(info -> !Objects.equals(remoteHash, branchesCollection.getHash(info.getRemoteBranch())))
        .forEach(info -> result.put(info.getLocalBranch(), branchesCollection.getHash(info.getRemoteBranch())));
    }
    return result;
  }

  @NotNull
  private Map<String, Hash> lsRemote(@NotNull GitRepository repository, @NotNull GitRemote remote, @NotNull List<String> branchRefNames) {
    Map<String, Hash> result = newHashMap();
    VcsFileUtil.chunkArguments(branchRefNames).forEach(refs -> {
      List<String> params = newArrayList("--heads", remote.getName());
      params.addAll(refs);
      GitCommandResult lsRemoteResult = Git.getInstance().runCommand(() -> createLsRemoteHandler(repository, remote, params));
      if (lsRemoteResult.success()) {
        Map<String, String> hashWithNameMap = map2MapNotNull(lsRemoteResult.getOutput(), GitRefUtil::parseRefsLine);
        result.putAll(getResolvedHashes(hashWithNameMap));
        myAuthErrorMap.computeIfPresent(repository, (r, remotes) -> {
          remotes.remove(remote);
          return nullize(remotes);
        });
      }
      else if (lsRemoteResult.isAuthenticationFailed()) {
        myAuthErrorMap.compute(repository, (repo, remoteSet) -> {
          if (remoteSet == null) return newHashSet(remote);
          remoteSet.add(remote);
          return remoteSet;
        });
      }
    });
    return result;
  }

  @NotNull
  private GitLineHandler createLsRemoteHandler(@NotNull GitRepository repository, @NotNull GitRemote remote, @NotNull List<String> params) {
    GitLineHandler h = new GitLineHandler(myProject, repository.getRoot(), GitCommand.LS_REMOTE,
                                          !myUseForceAuthentication ? singletonList("credential.helper=") : emptyList());
    h.setIgnoreAuthenticationRequest(!myUseForceAuthentication);
    h.addParameters(params);
    h.setUrls(remote.getUrls());
    return h;
  }

  private boolean shouldUpdateBranchesToPull(@NotNull GitRepository repository) {
    Map<GitLocalBranch, Hash> cachedBranchesToPull = myLocalBranchesToPull.get(repository);
    return cachedBranchesToPull == null || of(repository.getBranchTrackInfos()).anyMatch(
      info -> !Objects.equals(repository.getBranches().getHash(info.getRemoteBranch()), cachedBranchesToPull.get(info.getLocalBranch())));
  }

  @NotNull
  private static Map<GitLocalBranch, Hash> calculateBranchesToPush(@NotNull GitRepository gitRepository) {
    Map<GitLocalBranch, Hash> branchesToPush = newHashMap();
    GitBranchesCollection branchesCollection = gitRepository.getBranches();
    for (GitLocalBranch branch : branchesCollection.getLocalBranches()) {
      GitPushTarget pushTarget = GitPushSupport.getPushTargetIfExist(gitRepository, branch);
      Hash remoteHash = pushTarget != null ? branchesCollection.getHash(pushTarget.getBranch()) : null;
      if (remoteHash != null && !Objects.equals(branchesCollection.getHash(branch), remoteHash)) {
        branchesToPush.put(branch, remoteHash);
      }
    }
    return branchesToPush;
  }

  @NotNull
  private Collection<GitLocalBranch> getBranches(@Nullable GitRepository repository,
                                                 @NotNull Map<GitRepository, Map<GitLocalBranch, Hash>> branchCollection) {
    if (repository != null) {
      Map<GitLocalBranch, Hash> branchHashMap = branchCollection.get(repository);
      return branchHashMap != null ? branchHashMap.keySet() : Collections.emptySet();
    }
    return of(myLocalBranchesToPush.values()).flatMap(StreamEx::ofKeys).collect(toSet());
  }

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    myLocalBranchesToPush.put(repository, calculateBranchesToPush(repository));
    if (shouldUpdateBranchesToPull(repository)) {
      myLocalBranchesToPull.put(repository, calculateBranchesToPull(repository));
    }
  }

  @Override
  public void authenticationSucceeded(@NotNull GitRepository repository, @NotNull GitRemote remote) {
    // don't need to recalculate info if force authentication is already in use
    if (myUseForceAuthentication) return;

    Set<GitRemote> remotes = myAuthErrorMap.get(repository);
    if (remotes.contains(remote)) {
      Map<GitRemote, List<GitBranchTrackInfo>> trackInfoByRemotes = groupTrackInfoByRemotes(repository);
      if (trackInfoByRemotes.containsKey(remote)) {
        final Map<GitLocalBranch, Hash> newBranchMap = calcBranchesToPullForRemote(repository, remote, trackInfoByRemotes.get(remote));
        myLocalBranchesToPull.compute(repository, (r, branchHashMap) -> {
          if (branchHashMap == null) {
            return newHashMap(newBranchMap);
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
  private static Map<GitRemote, List<GitBranchTrackInfo>> groupTrackInfoByRemotes(@NotNull GitRepository repository) {
    Map<GitRemote, List<GitBranchTrackInfo>> trackInfosByRemote = newHashMap();
    Collection<GitBranchTrackInfo> trackInfos = repository.getBranchTrackInfos();
    for (GitBranchTrackInfo info : trackInfos) {
      if (trackInfosByRemote.containsKey(info.getRemote())) {
        trackInfosByRemote.get(info.getRemote()).add(info);
      }
      else {
        trackInfosByRemote.put(info.getRemote(), newArrayList(info));
      }
    }
    return trackInfosByRemote;
  }
}
