// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import com.intellij.dvcs.repo.Repository;
import com.intellij.vcs.log.Hash;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

class GitBranchState {
  private final @Nullable String currentRevision;
  private final @Nullable GitLocalBranch currentBranch;
  private final @NotNull Repository.State state;
  private final @NotNull Map<GitLocalBranch, Hash> localBranches;
  private final @NotNull Map<GitRemoteBranch, Hash> remoteBranches;

  GitBranchState(@Nullable String currentRevision,
                 @Nullable GitLocalBranch currentBranch,
                 @NotNull Repository.State state,
                 @NotNull Map<GitLocalBranch, Hash> localBranches,
                 @NotNull Map<GitRemoteBranch, Hash> remoteBranches) {
    this.currentRevision = currentRevision;
    this.currentBranch = currentBranch;
    this.state = state;
    this.localBranches = localBranches;
    this.remoteBranches = remoteBranches;
  }

  public @Nullable String getCurrentRevision() {
    return currentRevision;
  }

  public @Nullable GitLocalBranch getCurrentBranch() {
    return currentBranch;
  }

  public @NotNull Repository.State getState() {
    return state;
  }

  public @NotNull Map<GitLocalBranch, Hash> getLocalBranches() {
    return localBranches;
  }

  public @NotNull Map<GitRemoteBranch, Hash> getRemoteBranches() {
    return remoteBranches;
  }
}
