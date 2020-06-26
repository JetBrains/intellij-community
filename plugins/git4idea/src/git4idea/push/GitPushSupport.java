// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push;

import com.intellij.dvcs.push.*;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitStandardRemoteBranch;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.config.GitSharedSettings;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersionSpecialty;
import git4idea.repo.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static git4idea.GitUtil.findRemoteBranch;
import static git4idea.GitUtil.getDefaultOrFirstRemote;

public final class GitPushSupport extends PushSupport<GitRepository, GitPushSource, GitPushTarget> {

  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final GitVcs myVcs;
  @NotNull private final Pusher<GitRepository, GitPushSource, GitPushTarget> myPusher;
  @NotNull private final OutgoingCommitsProvider<GitRepository, GitPushSource, GitPushTarget> myOutgoingCommitsProvider;
  @NotNull private final GitVcsSettings mySettings;
  private final GitSharedSettings mySharedSettings;
  @NotNull private final PushSettings myCommonPushSettings;

  // instantiated from plugin.xml
  @SuppressWarnings("UnusedDeclaration")
  private GitPushSupport(@NotNull Project project) {
    myRepositoryManager = GitRepositoryManager.getInstance(project);
    myVcs = GitVcs.getInstance(project);
    mySettings = GitVcsSettings.getInstance(project);
    myPusher = new GitPusher(project, mySettings, this);
    myOutgoingCommitsProvider = new GitOutgoingCommitsProvider(project);
    mySharedSettings = ServiceManager.getService(project, GitSharedSettings.class);
    myCommonPushSettings = ServiceManager.getService(project, PushSettings.class);
  }

  @NotNull
  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }

  @NotNull
  @Override
  public Pusher<GitRepository, GitPushSource, GitPushTarget> getPusher() {
    return myPusher;
  }

  @NotNull
  @Override
  public OutgoingCommitsProvider<GitRepository, GitPushSource, GitPushTarget> getOutgoingCommitsProvider() {
    return myOutgoingCommitsProvider;
  }

  @Nullable
  @Override
  public GitPushTarget getDefaultTarget(@NotNull GitRepository repository) {
    if (repository.isFresh()) {
      return null;
    }
    GitLocalBranch sourceBranch = repository.getCurrentBranch();
    if (sourceBranch == null) {
      return null;
    }
    return getDefaultTarget(repository, GitPushSource.create(sourceBranch));
  }

  @Nullable
  @Override
  public GitPushTarget getDefaultTarget(@NotNull GitRepository repository, @NotNull GitPushSource source) {
    if (source instanceof GitPushSource.DetachedHead) return null;
    GitPushTarget pushSpecTarget = getPushTargetIfExist(repository, source.getBranch());
    if (pushSpecTarget != null) return pushSpecTarget;
    return proposeTargetForNewBranch(repository, source.getBranch());
  }

  @Nullable
  public static GitPushTarget getPushTargetIfExist(@NotNull GitRepository repository, @NotNull GitLocalBranch localBranch) {
    GitPushTarget pushSpecTarget = GitPushTarget.getFromPushSpec(repository, localBranch);
    if (pushSpecTarget != null) {
      return pushSpecTarget;
    }

    GitBranchTrackInfo trackInfo = GitBranchUtil.getTrackInfoForBranch(repository, localBranch);
    if (trackInfo != null) {
      return new GitPushTarget(trackInfo.getRemoteBranch(), false);
    }
    return null;
  }

  private static GitPushTarget proposeTargetForNewBranch(@NotNull GitRepository repository, @NotNull GitLocalBranch sourceBranch) {
    GitRemote remote = getDefaultOrFirstRemote(repository.getRemotes());
    if (remote == null) return null; // TODO need to propose to declare new remote
    return makeTargetForNewBranch(repository, remote, sourceBranch);
  }

  @NotNull
  private static GitPushTarget makeTargetForNewBranch(@NotNull GitRepository repository,
                                                      @NotNull GitRemote remote,
                                                      @NotNull GitLocalBranch sourceBranch) {
    GitRemoteBranch existingRemoteBranch = findRemoteBranch(repository, remote, sourceBranch.getName());
    if (existingRemoteBranch != null) {
      return new GitPushTarget(existingRemoteBranch, false);
    }
    return new GitPushTarget(new GitStandardRemoteBranch(remote, sourceBranch.getName()), true);
  }

  @NotNull
  @Override
  public GitPushSource getSource(@NotNull GitRepository repository) {
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    return currentBranch != null
           ? GitPushSource.create(currentBranch)
           : GitPushSource.create(Objects.requireNonNull(repository.getCurrentRevision())); // fresh repository is on branch
  }

  @NotNull
  @Override
  public RepositoryManager<GitRepository> getRepositoryManager() {
    return myRepositoryManager;
  }

  @NotNull
  @Override
  public PushTargetPanel<GitPushTarget> createTargetPanel(@NotNull GitRepository repository,
                                                          @NotNull GitPushSource source,
                                                          @Nullable GitPushTarget defaultTarget) {
    return new GitPushTargetPanel(this, repository, source, defaultTarget);
  }

  @Override
  public boolean isForcePushAllowed(@NotNull GitRepository repo, @NotNull GitPushTarget target) {
    final String targetBranch = target.getBranch().getNameForRemoteOperations();
    return !mySharedSettings.isBranchProtected(targetBranch);
  }

  @Nullable
  @Override
  public VcsPushOptionsPanel createOptionsPanel() {
    return new GitPushOptionsPanel(mySettings.getPushTagMode(),
                                   GitVersionSpecialty.SUPPORTS_FOLLOW_TAGS.existsIn(myVcs),
                                   shouldShowSkipHookOption());
  }

  private boolean shouldShowSkipHookOption() {
    return GitVersionSpecialty.PRE_PUSH_HOOK.existsIn(myVcs) &&
           getRepositoryManager()
             .getRepositories()
             .stream()
             .map(e -> e.getInfo().getHooksInfo())
             .anyMatch(GitHooksInfo::isPrePushHookAvailable);
  }

  @Override
  public boolean isSilentForcePushAllowed(@NotNull GitPushTarget target) {
    return myCommonPushSettings.containsForcePushTarget(target.getBranch().getRemote().getName(),
                                                        target.getBranch().getNameForRemoteOperations());
  }

  @Override
  public void saveSilentForcePushTarget(@NotNull GitPushTarget target) {
    myCommonPushSettings.addForcePushTarget(target.getBranch().getRemote().getName(), target.getBranch().getNameForRemoteOperations());
  }

  @Override
  public boolean mayChangeTargetsSync() {
    return true;
  }
}
