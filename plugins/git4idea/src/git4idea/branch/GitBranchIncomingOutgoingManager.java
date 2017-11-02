// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitLocalBranch;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.config.GitVcsSettings;
import git4idea.push.GitPushSupport;
import git4idea.push.GitPushTarget;
import git4idea.repo.*;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static git4idea.repo.GitRefUtil.addRefsHeadsPrefixIfNeeded;
import static git4idea.repo.GitRefUtil.getResolvedHashes;
import static java.util.stream.Collectors.toSet;
import static one.util.streamex.StreamEx.of;

public class GitBranchIncomingOutgoingManager implements GitRepositoryChangeListener {

  final Map<GitRepository, Collection<GitLocalBranch>> myLocalBranchesToPull = ContainerUtil.newConcurrentMap();
  final Map<GitRepository, Collection<GitLocalBranch>> myLocalBranchesToPush = ContainerUtil.newConcurrentMap();
  @NotNull private final Project myProject;
  @Nullable private ScheduledFuture<?> myPeriodicalUpdater;

  GitBranchIncomingOutgoingManager(@NotNull Project project) {
    myProject = project;
  }

  public static GitBranchIncomingOutgoingManager getInstance(Project project) {
    return ServiceManager.getService(project, GitBranchIncomingOutgoingManager.class);
  }

  @CalledInAwt
  public void startScheduling() {
    myProject.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, this);
    if (myPeriodicalUpdater == null) {
      myPeriodicalUpdater = JobScheduler.getScheduler()
        .scheduleWithFixedDelay(() -> updateBranchInfo(), 1, GitVcsSettings.getInstance(myProject).getBranchInfoUpdateTime(),
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

  @NotNull
  public Collection<GitLocalBranch> getBranchesToPull(@Nullable GitRepository repository) {
    return getBranches(repository, myLocalBranchesToPull);
  }

  @NotNull
  public Collection<GitLocalBranch> getBranchesToPush(@Nullable GitRepository repository) {
    return getBranches(repository, myLocalBranchesToPush);
  }

  @CalledInBackground
  private void updateBranchInfo() {
    for (GitRepository repository : GitRepositoryManager.getInstance(myProject).getRepositories()) {
      myLocalBranchesToPull.put(repository, calculateBranchesToPull(repository));
    }
  }

  @NotNull
  private List<GitLocalBranch> calculateBranchesToPull(@NotNull GitRepository repository) {
    List<GitLocalBranch> result = ContainerUtil.newArrayList();
    GitBranchesCollection branchesCollection = repository.getBranches();
    Map<GitRemote, List<GitBranchTrackInfo>> trackInfoByRemotes = groupTrackInfoByRemotes(repository);
    for (Map.Entry<GitRemote, List<GitBranchTrackInfo>> entry : trackInfoByRemotes.entrySet()) {
      List<GitBranchTrackInfo> trackInfoList = entry.getValue();
      final Map<String, Hash> remoteNameWithHash = lsRemote(repository, entry.getKey(), trackInfoList);
      for (Map.Entry<String, Hash> hashEntry : remoteNameWithHash.entrySet()) {
        String remoteBranchName = hashEntry.getKey();
        Hash remoteHash = hashEntry.getValue();
        of(trackInfoList)
          .filter(
            info -> StringUtil.equals(remoteBranchName, addRefsHeadsPrefixIfNeeded(info.getRemoteBranch().getNameForRemoteOperations())))
          .map(GitBranchTrackInfo::getLocalBranch)
          .filter(l -> !Objects.equals(remoteHash, branchesCollection.getHash(l)))
          .forEach(result::add);
      }
    }
    return result;
  }

  @NotNull
  private Map<String, Hash> lsRemote(@NotNull GitRepository repository,
                                     @NotNull GitRemote remote,
                                     @NotNull List<GitBranchTrackInfo> trackInfos) {
    List<String> branchRefNames = ContainerUtil.map(trackInfos, info -> info.getRemoteBranch().getNameForRemoteOperations());
    Map<String, Hash> result = ContainerUtil.newHashMap();
    VcsFileUtil.chunkArguments(branchRefNames).forEach(refs -> {
      List<String> params = ContainerUtil.newArrayList("--heads", remote.getName());
      params.addAll(refs);
      GitCommandResult lsRemoteResult =
        Git.getInstance().lsRemote(myProject, repository.getRoot(), remote, ArrayUtil.toStringArray(params));
      if (lsRemoteResult.success()) {
        Map<String, String> hashWithNameMap = ContainerUtil.map2MapNotNull(lsRemoteResult.getOutput(), GitRefUtil::parseRefsLine);
        result.putAll(getResolvedHashes(hashWithNameMap));
      }
    });
    return result;
  }

  private void updateBranchInfoToPush(@NotNull GitRepository gitRepository) {
    Collection<GitLocalBranch> branchesToPush = ContainerUtil.newArrayList();
    GitBranchesCollection branchesCollection = gitRepository.getBranches();
    for (GitLocalBranch branch : branchesCollection.getLocalBranches()) {
      GitPushTarget pushTarget = GitPushSupport.getPushTargetIfExist(gitRepository, branch);
      Hash remoteHash = pushTarget != null ? branchesCollection.getHash(pushTarget.getBranch()) : null;
      if (!Objects.equals(branchesCollection.getHash(branch), remoteHash)) {
        branchesToPush.add(branch);
      }
    }
    myLocalBranchesToPush.put(gitRepository, branchesToPush);
  }

  @NotNull
  private Collection<GitLocalBranch> getBranches(@Nullable GitRepository repository,
                                                 @NotNull Map<GitRepository, Collection<GitLocalBranch>> branchCollection) {
    if (repository != null) return ObjectUtils.chooseNotNull(branchCollection.get(repository), Collections.emptyList());
    return of(myLocalBranchesToPush.values()).flatMap(Collection::stream).collect(toSet());
  }

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    updateBranchInfoToPush(repository);
  }

  @NotNull
  private static Map<GitRemote, List<GitBranchTrackInfo>> groupTrackInfoByRemotes(@NotNull GitRepository repository) {
    Map<GitRemote, List<GitBranchTrackInfo>> trackInfosByRemote = ContainerUtil.newHashMap();
    Collection<GitBranchTrackInfo> trackInfos = repository.getBranchTrackInfos();
    for (GitBranchTrackInfo info : trackInfos) {
      if (trackInfosByRemote.containsKey(info.getRemote())) {
        trackInfosByRemote.get(info.getRemote()).add(info);
      }
      else {
        trackInfosByRemote.put(info.getRemote(), ContainerUtil.newArrayList(info));
      }
    }
    return trackInfosByRemote;
  }
}
