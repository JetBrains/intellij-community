// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import com.intellij.dvcs.push.PushTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitStandardRemoteBranch;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ParseException;
import java.util.Collection;
import java.util.List;

import static git4idea.GitBranch.REFS_HEADS_PREFIX;
import static git4idea.GitBranch.REFS_REMOTES_PREFIX;
import static git4idea.GitUtil.findRemoteBranch;
import static git4idea.GitUtil.getDefaultOrFirstRemote;

public class GitPushTarget implements PushTarget {

  private static final Logger LOG = Logger.getInstance(GitPushTarget.class);

  private final @NotNull GitRemoteBranch myRemoteBranch;
  private final boolean myIsNewBranchCreated;
  private final boolean myPushingToSpecialRef;
  private final @Nullable GitPushTargetType myTargetType;

  private boolean shouldSetNewUpstream;

  public GitPushTarget(@NotNull GitRemoteBranch remoteBranch, boolean isNewBranchCreated) {
    this(remoteBranch, isNewBranchCreated, false, null);
  }

  public GitPushTarget(@NotNull GitRemoteBranch remoteBranch,
                       boolean isNewBranchCreated,
                       @NotNull GitPushTargetType targetType) {
    this(remoteBranch, isNewBranchCreated, false, targetType);
  }

  public GitPushTarget(@NotNull GitRemoteBranch remoteBranch,
                       boolean isNewBranchCreated,
                       boolean isPushingToSpecialRef,
                       @Nullable GitPushTargetType targetType) {
    myRemoteBranch = remoteBranch;
    myIsNewBranchCreated = isNewBranchCreated;
    myPushingToSpecialRef = isPushingToSpecialRef;
    myTargetType = targetType;
  }

  public @NotNull GitRemoteBranch getBranch() {
    return myRemoteBranch;
  }

  @Override
  public boolean hasSomethingToPush() {
    return isNewBranchCreated();
  }

  @Override
  public @NotNull String getPresentation() {
    return myPushingToSpecialRef ? myRemoteBranch.getFullName() : myRemoteBranch.getNameForRemoteOperations();
  }

  public boolean isNewBranchCreated() {
    return myIsNewBranchCreated;
  }

  public boolean isSpecialRef() {
    return myPushingToSpecialRef;
  }

  public void shouldSetNewUpstream(boolean value) {
    shouldSetNewUpstream = value;
  }

  public boolean shouldSetUpstream(@NotNull GitPushSource pushSource, @NotNull GitRepository repository) {
    GitLocalBranch sourceBranch = pushSource.getBranch();

    return sourceBranch != null &&
           pushSource.isBranchRef() &&
           ((isNewBranchCreated() && !branchTrackingInfoIsSet(repository, sourceBranch)) || shouldSetNewUpstream);
  }

  public @Nullable GitPushTargetType getTargetType() {
    return myTargetType;
  }

  public static @NotNull GitPushTarget parse(@NotNull GitRepository repository, @Nullable String remoteName, @NotNull String branchName) throws
                                                                                                                        ParseException {
    if (remoteName == null) {
      throw new ParseException("No remotes defined", -1);
    }

    if (!GitRefNameValidator.getInstance().checkInput(branchName)) {
      throw new ParseException("Invalid destination branch name: " + branchName, -1);
    }

    GitRemote remote = findRemote(repository.getRemotes(), remoteName);
    if (remote == null) {
      LOG.error("Remote [" + remoteName + "] is not found among " + repository.getRemotes());
      throw new ParseException("Invalid remote: " + remoteName, -1);
    }

    GitRemoteBranch existingRemoteBranch = findRemoteBranch(repository, remote, branchName);
    if (existingRemoteBranch != null) {
      return new GitPushTarget(existingRemoteBranch, false, GitPushTargetType.CUSTOM);
    }
    GitRemoteBranch rb = new GitStandardRemoteBranch(remote, branchName);
    return new GitPushTarget(rb, true, GitPushTargetType.CUSTOM);
  }

  static @Nullable GitRemote findRemote(@NotNull Collection<GitRemote> remotes, final @NotNull String candidate) {
    return ContainerUtil.find(remotes, remote -> remote.getName().equals(candidate));
  }

  public static @Nullable GitPushTarget getFromPushSpec(@NotNull GitRepository repository, @NotNull GitLocalBranch sourceBranch) {
    final GitRemote remote = getRemoteToPush(repository, GitBranchUtil.getTrackInfoForBranch(repository, sourceBranch));
    return remote == null ? null : getFromPushSpec(repository, remote, sourceBranch);
  }

  public static @Nullable GitPushTarget getFromPushSpec(@NotNull GitRepository repository, @NotNull GitRemote remote, @NotNull GitLocalBranch sourceBranch) {
    List<String> specs = remote.getPushRefSpecs();
    if (specs.isEmpty()) return null;

    String targetRef = GitPushSpecParser.getTargetRef(repository, sourceBranch, specs);
    if (targetRef == null) return null;

    String remotePrefix = REFS_REMOTES_PREFIX + remote.getName() + "/";
    if (targetRef.startsWith(REFS_HEADS_PREFIX)) {
      targetRef = targetRef.substring(REFS_HEADS_PREFIX.length());
      GitRemoteBranch remoteBranch = GitUtil.findOrCreateRemoteBranch(repository, remote, targetRef);
      boolean existingBranch = repository.getBranches().getRemoteBranches().contains(remoteBranch);
      return new GitPushTarget(remoteBranch, !existingBranch, GitPushTargetType.PUSH_SPEC);
    }
    if (targetRef.startsWith(remotePrefix)) {
      targetRef = targetRef.substring(remotePrefix.length());
      GitRemoteBranch remoteBranch = GitUtil.findOrCreateRemoteBranch(repository, remote, targetRef);
      boolean existingBranch = repository.getBranches().getRemoteBranches().contains(remoteBranch);
      return new GitPushTarget(remoteBranch, !existingBranch, GitPushTargetType.PUSH_SPEC);
    }
    else {
      GitRemoteBranch remoteBranch = new GitSpecialRefRemoteBranch(targetRef, remote);
      return new GitPushTarget(remoteBranch, true, true, GitPushTargetType.PUSH_SPEC);
    }
  }

  private static @Nullable GitRemote getRemoteToPush(@NotNull GitRepository repository, @Nullable GitBranchTrackInfo trackInfo) {
    if (trackInfo != null) {
      return trackInfo.getRemote();
    }
    return getDefaultOrFirstRemote(repository.getRemotes());
  }

  private static boolean branchTrackingInfoIsSet(@NotNull GitRepository repository, final @NotNull GitLocalBranch source) {
    return ContainerUtil.exists(repository.getBranchTrackInfos(), info -> info.getLocalBranch().equals(source));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GitPushTarget target)) return false;

    if (myIsNewBranchCreated != target.myIsNewBranchCreated) return false;
    if (!myRemoteBranch.equals(target.myRemoteBranch)) return false;
    if (shouldSetNewUpstream != target.shouldSetNewUpstream) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myRemoteBranch.hashCode();
    result = 31 * result + (myIsNewBranchCreated ? 1 : 0);
    result = 31 * result + (shouldSetNewUpstream ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return myRemoteBranch.getNameForLocalOperations();
  }
}
