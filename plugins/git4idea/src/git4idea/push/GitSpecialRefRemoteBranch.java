// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push;

import git4idea.GitRemoteBranch;
import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;

/**
 * Semi-fake remote branch if pushing to special push specs like "HEAD:refs/for/master".
 */
public class GitSpecialRefRemoteBranch extends GitRemoteBranch {
  private final String myRef;
  private final GitRemote myRemote;

  public GitSpecialRefRemoteBranch(@NotNull String ref, @NotNull GitRemote remote) {
    super(ref);
    myRef = ref;
    myRemote = remote;
  }

  @Override
  public @NotNull String getNameForRemoteOperations() {
    return myRef;
  }

  @Override
  public @NotNull String getNameForLocalOperations() {
    return myRef;
  }

  @Override
  public @NotNull GitRemote getRemote() {
    return myRemote;
  }

  @Override
  public @NotNull String getFullName() {
    return myRef;
  }
}
