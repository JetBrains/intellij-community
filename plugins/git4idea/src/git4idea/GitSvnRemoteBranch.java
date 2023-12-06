// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea;

import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;

/**
 * <p>The naming conventions of SVN remote branches are slightly different from the ordinary remote branches.</p>
 *
 * <p>No remote is specified: dot (".") is used as a remote.</p>
 * <p>Remote branch name has "refs/remotes/branch" format, i. e. it doesn't have a remote prefix.</p>
 *
 * <p>Because of these differences, GitSvnRemoteBranch doesn't {@link GitStandardRemoteBranch}. </p>
 *
 * @author Kirill Likhodedov
 */
public class GitSvnRemoteBranch extends GitRemoteBranch {

  public GitSvnRemoteBranch(@NotNull String fullName) {
    super(fullName);
  }

  @Override
  public @NotNull String getNameForRemoteOperations() {
    return getFullName();
  }

  @Override
  public @NotNull String getNameForLocalOperations() {
    return getFullName();
  }

  @Override
  public @NotNull GitRemote getRemote() {
    return GitRemote.DOT;
  }

  @Override
  public @NotNull String getFullName() {
    return getName();
  }
}
