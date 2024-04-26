// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import com.intellij.dvcs.push.*;
import com.intellij.dvcs.repo.RepositoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
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

import java.util.Set;

import static git4idea.GitUtil.findRemoteBranch;
import static git4idea.GitUtil.getDefaultOrFirstRemote;

public final class GitPushSupport extends PushSupport<GitRepository, GitPushSource, GitPushTarget> {

  private final @NotNull GitRepositoryManager myRepositoryManager;
  private final @NotNull GitVcs myVcs;
  private final @NotNull Pusher<GitRepository, GitPushSource, GitPushTarget> myPusher;
  private final @NotNull OutgoingCommitsProvider<GitRepository, GitPushSource, GitPushTarget> myOutgoingCommitsProvider;
  private final @NotNull GitVcsSettings mySettings;
  private final GitSharedSettings mySharedSettings;
  private final @NotNull PushSettings myCommonPushSettings;

  // instantiated from plugin.xml
  @SuppressWarnings("UnusedDeclaration")
  private GitPushSupport(@NotNull Project project) {
    myRepositoryManager = GitRepositoryManager.getInstance(project);
    myVcs = GitVcs.getInstance(project);
    mySettings = GitVcsSettings.getInstance(project);
    myPusher = new GitPusher(project, mySettings, this);
    myOutgoingCommitsProvider = new GitOutgoingCommitsProvider(project);
    mySharedSettings = GitSharedSettings.getInstance(project);
    myCommonPushSettings = project.getService(PushSettings.class);
  }

  @Override
  public @NotNull AbstractVcs getVcs() {
    return myVcs;
  }

  @Override
  public @NotNull Pusher<GitRepository, GitPushSource, GitPushTarget> getPusher() {
    return myPusher;
  }

  @Override
  public @NotNull OutgoingCommitsProvider<GitRepository, GitPushSource, GitPushTarget> getOutgoingCommitsProvider() {
    return myOutgoingCommitsProvider;
  }

  @Override
  public boolean canBePushed(@NotNull GitRepository repository, @NotNull GitPushSource source, @NotNull GitPushTarget target) {
    if (repository.isFresh()) return false;
    return true;
  }

  @Override
  public @Nullable GitPushTarget getDefaultTarget(@NotNull GitRepository repository) {
    if (repository.isFresh()) {
      return null;
    }
    GitLocalBranch sourceBranch = repository.getCurrentBranch();
    if (sourceBranch == null) {
      return null;
    }
    return getDefaultTarget(repository, GitPushSource.create(sourceBranch));
  }

  @Override
  public @Nullable GitPushTarget getDefaultTarget(@NotNull GitRepository repository, @NotNull GitPushSource source) {
    GitLocalBranch sourceBranch = source.getBranch();
    if (sourceBranch != null) {
      GitPushTarget pushSpecTarget = getPushTargetIfExist(repository, sourceBranch);
      if (pushSpecTarget != null) return pushSpecTarget;
    }

    GitRemote remote = getDefaultOrFirstRemote(repository.getRemotes());
    if (remote == null) return null;

    if (sourceBranch != null) {
      return makeTargetForNewBranch(repository, remote, sourceBranch.getName());
    }

    GitLocalBranch currentBranch = repository.getCurrentBranch();
    if (currentBranch != null) {
      return makeTargetForNewBranch(repository, remote, currentBranch.getName());
    }

    Set<String> remoteBranches = ContainerUtil.map2SetNotNull(repository.getBranches().getRemoteBranches(), branch ->
      branch.getRemote().equals(remote) ? branch.getNameForRemoteOperations() : null
    );
    String newBranchName = UniqueNameGenerator.generateUniqueName("detached", "", "", remoteBranches);
    return makeTargetForNewBranch(repository, remote, newBranchName);
  }

  public static @Nullable GitPushTarget getPushTargetIfExist(@NotNull GitRepository repository, @NotNull GitLocalBranch localBranch) {
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

  private static @NotNull GitPushTarget makeTargetForNewBranch(@NotNull GitRepository repository,
                                                               @NotNull GitRemote remote,
                                                               @NotNull String branchName) {
    GitRemoteBranch existingRemoteBranch = findRemoteBranch(repository, remote, branchName);
    if (existingRemoteBranch != null) {
      return new GitPushTarget(existingRemoteBranch, false);
    }
    return new GitPushTarget(new GitStandardRemoteBranch(remote, branchName), true);
  }

  @Override
  public @Nullable GitPushSource getSource(@NotNull GitRepository repository) {
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    if (currentBranch != null) return GitPushSource.create(currentBranch);

    String currentRevision = repository.getCurrentRevision();
    if (currentRevision != null) return GitPushSource.createDetached(currentRevision);

    return null;
  }

  @Override
  public @NotNull RepositoryManager<GitRepository> getRepositoryManager() {
    return myRepositoryManager;
  }

  @Override
  public @NotNull PushTargetPanel<GitPushTarget> createTargetPanel(@NotNull GitRepository repository,
                                                                   @NotNull GitPushSource source,
                                                                   @Nullable GitPushTarget defaultTarget) {
    return new GitPushTargetPanel(this, repository, source, defaultTarget);
  }

  @Override
  public boolean isForcePushAllowed(@NotNull GitRepository repo, @NotNull GitPushTarget target) {
    final String targetBranch = target.getBranch().getNameForRemoteOperations();
    return !mySharedSettings.isBranchProtected(targetBranch);
  }

  @Override
  public @Nullable VcsPushOptionsPanel createOptionsPanel() {
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
