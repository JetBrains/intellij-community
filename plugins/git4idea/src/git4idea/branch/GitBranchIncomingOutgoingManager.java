// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.Hash;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
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
  @NotNull private final MultiMap<GitRepository, GitRemote> myAuthErrorMap = MultiMap.createConcurrentSet();
  @NotNull private final Project myProject;
  @Nullable private ScheduledFuture<?> myPeriodicalUpdater;
  @NotNull private final GitRepositoryManager myRepositoryManager;
  @Nullable private MessageBusConnection myConnection;

  GitBranchIncomingOutgoingManager(@NotNull Project project, @NotNull GitRepositoryManager repositoryManager) {
    myProject = project;
    myRepositoryManager = repositoryManager;
  }

  public boolean hasIncomingFor(@Nullable GitRepository repository, @NotNull String localBranchName) {
    return getBranchesToPull(repository).contains(new GitLocalBranch(localBranchName));
  }

  public boolean hasOutgoingFor(@Nullable GitRepository repository, @NotNull String localBranchName) {
    return getBranchesToPush(repository).contains(new GitLocalBranch(localBranchName));
  }

  @NotNull
  public static GitBranchIncomingOutgoingManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GitBranchIncomingOutgoingManager.class);
  }

  public boolean hasAuthenticationProblems() {
    return !myAuthErrorMap.isEmpty();
  }

  public void startScheduling() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      if (myConnection == null) {
        myConnection = myProject.getMessageBus().connect();
        myConnection.subscribe(GitRepository.GIT_REPO_CHANGE, this);
        myConnection.subscribe(GitAuthenticationListener.GIT_AUTHENTICATION_SUCCESS, this);
      }
      forceUpdateBranches(false);
      if (myPeriodicalUpdater == null) {
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
    });
  }

  @CalledInAwt
  public void stopScheduling() {
    if (myPeriodicalUpdater != null) {
      myPeriodicalUpdater.cancel(true);
      myPeriodicalUpdater = null;
    }
    if (myConnection != null) {
      myConnection.disconnect();
      myConnection = null;
    }
  }

  @CalledInAny
  public void forceUpdateBranches(boolean useForceAuthentication) {
    new Task.Backgroundable(myProject, "Update Branches Info...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        myRepositoryManager.getRepositories().forEach(r -> {
          myLocalBranchesToPush.put(r, calculateBranchesToPush(r));
          myLocalBranchesToPull.put(r, calculateBranchesToPull(r, useForceAuthentication));
        });
      }
    }.queue();
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
    myRepositoryManager.getRepositories().forEach(r -> myLocalBranchesToPull.put(r, calculateBranchesToPull(r, false)));
  }

  @NotNull
  private Map<GitLocalBranch, Hash> calculateBranchesToPull(@NotNull GitRepository repository, boolean useForceAuthentication) {
    Map<GitLocalBranch, Hash> result = newHashMap();
    groupTrackInfoByRemotes(repository).entrySet()
      .forEach(entry -> result.putAll(calcBranchesToPullForRemote(repository, entry.getKey(), entry.getValue(), useForceAuthentication)));
    return result;
  }

  @NotNull
  private Map<GitLocalBranch, Hash> calcBranchesToPullForRemote(@NotNull GitRepository repository,
                                                                @NotNull GitRemote gitRemote,
                                                                @NotNull Collection<GitBranchTrackInfo> trackInfoList,
                                                                boolean useForceAuthentication) {
    Map<GitLocalBranch, Hash> result = newHashMap();
    GitBranchesCollection branchesCollection = repository.getBranches();
    final Map<String, Hash> remoteNameWithHash =
      lsRemote(repository, gitRemote, map(trackInfoList, info -> info.getRemoteBranch().getNameForRemoteOperations()),
               useForceAuthentication);

    for (Map.Entry<String, Hash> hashEntry : remoteNameWithHash.entrySet()) {
      String remoteBranchName = hashEntry.getKey();
      Hash remoteHash = hashEntry.getValue();
      trackInfoList.forEach(info -> {
        GitRemoteBranch remoteBranch = info.getRemoteBranch();
        if (StringUtil.equals(remoteBranchName, addRefsHeadsPrefixIfNeeded(remoteBranch.getNameForRemoteOperations()))) {
          Hash localHashForRemoteBranch = branchesCollection.getHash(remoteBranch);
          if (localHashForRemoteBranch != null && !localHashForRemoteBranch.equals(remoteHash)) {
            result.put(info.getLocalBranch(), localHashForRemoteBranch);
          }
        }
      });
    }
    return result;
  }

  @NotNull
  private Map<String, Hash> lsRemote(@NotNull GitRepository repository,
                                     @NotNull GitRemote remote,
                                     @NotNull List<String> branchRefNames,
                                     boolean useForceAuthentication) {
    Map<String, Hash> result = newHashMap();
    VcsFileUtil.chunkArguments(branchRefNames).forEach(refs -> {
      List<String> params = newArrayList("--heads", remote.getName());
      params.addAll(refs);
      GitCommandResult lsRemoteResult = Git.getInstance().runCommand(() -> createLsRemoteHandler(repository, remote, params,
                                                                                                 useForceAuthentication));
      if (lsRemoteResult.success()) {
        Map<String, String> hashWithNameMap = map2MapNotNull(lsRemoteResult.getOutput(), GitRefUtil::parseRefsLine);
        result.putAll(getResolvedHashes(hashWithNameMap));
        myAuthErrorMap.remove(repository, remote);
      }
      else if (lsRemoteResult.isAuthenticationFailed()) {
        myAuthErrorMap.putValue(repository, remote);
      }
    });
    return result;
  }

  @NotNull
  private GitLineHandler createLsRemoteHandler(@NotNull GitRepository repository,
                                               @NotNull GitRemote remote,
                                               @NotNull List<String> params, boolean useForceAuthentication) {
    GitLineHandler h = new GitLineHandler(myProject, repository.getRoot(), GitCommand.LS_REMOTE,
                                          !useForceAuthentication ? singletonList("credential.helper=") : emptyList());
    h.setIgnoreAuthenticationRequest(!useForceAuthentication);
    h.addParameters(params);
    h.setUrls(remote.getUrls());
    return h;
  }

  private boolean shouldUpdateBranchesToPull(@NotNull GitRepository repository) {
    Map<GitLocalBranch, Hash> cachedBranchesToPull = myLocalBranchesToPull.get(repository);
    return cachedBranchesToPull == null ||
           of(repository.getBranchTrackInfos())
             .anyMatch(info ->
                         !Objects.equals(repository.getBranches().getHash(info.getRemoteBranch()),
                                         cachedBranchesToPull.get(info.getLocalBranch())));
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
    myLocalBranchesToPush.put(repository, calculateBranchesToPush(repository));
    if (shouldUpdateBranchesToPull(repository)) {
      myLocalBranchesToPull.put(repository, calculateBranchesToPull(repository, false));
    }
  }

  @Override
  public void authenticationSucceeded(@NotNull GitRepository repository, @NotNull GitRemote remote) {
    Collection<GitRemote> remotes = myAuthErrorMap.get(repository);
    if (remotes.contains(remote)) {
      MultiMap<GitRemote, GitBranchTrackInfo> trackInfoByRemotes = groupTrackInfoByRemotes(repository);
      if (trackInfoByRemotes.containsKey(remote)) {
        final Map<GitLocalBranch, Hash> newBranchMap = calcBranchesToPullForRemote(repository, remote, trackInfoByRemotes.get(remote),
                                                                                   false);
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
  private static MultiMap<GitRemote, GitBranchTrackInfo> groupTrackInfoByRemotes(@NotNull GitRepository repository) {
    return groupBy(repository.getBranchTrackInfos(), GitBranchTrackInfo::getRemote);
  }
}