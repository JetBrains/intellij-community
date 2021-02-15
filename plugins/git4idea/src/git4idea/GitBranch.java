// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea;

import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a Git branch, local or remote.
 *
 * <p>Local and remote branches are different in that nature, that remote branch has complex name ("origin/master") containing both
 *    the name of the remote and the name of the branch. And while the standard name of the remote branch if origin/master,
 *    in certain cases we must operate with the name which the branch has at the remote: "master".</p>
 *
 * <p>It contains information about the branch name and the hash it points to.
 *    Note that the object (including the hash) is immutable. That means that if branch reference move along, you have to get new instance
 *    of the GitBranch object, probably from {@link GitRepository#getBranches()} or {@link GitRepository#getCurrentBranch()}.
 * </p>
 *
 * <p>GitBranches are equal, if their full names are equal. That means that if two GitBranch objects have different hashes, they
 *    are considered equal. But in this case an error if logged, because it means that one of this GitBranch instances is out-of-date, and
 *    it is required to use an {@link GitRepository#update()} updated} version.</p>
 */
public abstract class GitBranch extends GitReference {

  @NonNls public static final String REFS_HEADS_PREFIX = "refs/heads/"; // Prefix for local branches ({@value})
  @NonNls public static final String REFS_REMOTES_PREFIX = "refs/remotes/"; // Prefix for remote branches ({@value})

  protected GitBranch(@NotNull String name) {
    super(GitBranchUtil.stripRefsPrefix(name));
  }

  /**
   * @return true if the branch is remote
   */
  public abstract boolean isRemote();

  @Override
  @NotNull
  public String getFullName() {
    return (isRemote() ? REFS_REMOTES_PREFIX : REFS_HEADS_PREFIX) + myName;
  }
}
