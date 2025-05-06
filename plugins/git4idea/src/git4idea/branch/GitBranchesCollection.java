// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitReference;
import git4idea.GitRemoteBranch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * Storage for local, remote and recent checkout branches.
 * The reason of creating this special collection is that
 * in the terms of performance, they are detected by {@link git4idea.repo.GitRepositoryReader} at once;
 * and also usually both sets of branches are needed by components, but are treated differently,
 * so it is more convenient to have them separated, but in a single container.
 * </p>
 */
public final class GitBranchesCollection {
  private static final int MAX_RECENT_CHECKOUT_BRANCHES = 5;

  private final @NotNull Map<GitLocalBranch, Hash> myLocalBranches;
  private final @NotNull Map<GitRemoteBranch, Hash> myRemoteBranches;

  private final @NotNull List<GitLocalBranch> myRecentCheckoutBranches;

  public GitBranchesCollection(@NotNull Map<GitLocalBranch, Hash> localBranches,
                               @NotNull Map<GitRemoteBranch, Hash> remoteBranches,
                               @NotNull List<GitLocalBranch> recentCheckoutBranches) {
    myRemoteBranches = remoteBranches;
    myLocalBranches = localBranches;
    myRecentCheckoutBranches = ContainerUtil.getFirstItems(recentCheckoutBranches, MAX_RECENT_CHECKOUT_BRANCHES);
  }

  public @NotNull List<GitLocalBranch> getRecentCheckoutBranches() {
    return Collections.unmodifiableList(myRecentCheckoutBranches);
  }

  public @NotNull Collection<GitLocalBranch> getLocalBranches() {
    return Collections.unmodifiableCollection(myLocalBranches.keySet());
  }

  public @NotNull Collection<GitRemoteBranch> getRemoteBranches() {
    return Collections.unmodifiableCollection(myRemoteBranches.keySet());
  }

  public @Nullable Hash getHash(@NotNull GitBranch branch) {
    if (branch instanceof GitLocalBranch) return myLocalBranches.get(branch);
    if (branch instanceof GitRemoteBranch) return myRemoteBranches.get(branch);
    return null;
  }

  public @NotNull Collection<GitLocalBranch> findLocalBranchesByHash(@NotNull Hash hash) {
    return ContainerUtil.filter(myLocalBranches.keySet(), key -> myLocalBranches.get(key).equals(hash));
  }

  public @NotNull Collection<GitRemoteBranch> findRemoteBranchesByHash(@NotNull Hash hash) {
    return ContainerUtil.filter(myRemoteBranches.keySet(), key -> myRemoteBranches.get(key).equals(hash));
  }

  public @Nullable GitLocalBranch findLocalBranch(@NotNull String name) {
    GitLocalBranch branch = new GitLocalBranch(name);
    return myLocalBranches.containsKey(branch) ? branch : null;
  }

  public @Nullable GitRemoteBranch findRemoteBranch(@NotNull String name) {
    return findByName(myRemoteBranches.keySet(), name);
  }

  public @Nullable GitBranch findBranchByName(@NotNull String name) {
    GitLocalBranch result = findLocalBranch(name);
    return result == null ? findRemoteBranch(name) : result;
  }

  private static @Nullable <T extends GitBranch> T findByName(@NotNull Collection<T> branches, @NotNull String name) {
    return ContainerUtil.find(branches, branch -> GitReference.BRANCH_NAME_HASHING_STRATEGY.equals(name, branch.getName()));
  }
}
