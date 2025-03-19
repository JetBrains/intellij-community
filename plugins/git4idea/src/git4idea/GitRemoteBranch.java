// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea;

import com.intellij.openapi.util.NlsSafe;
import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
public abstract class GitRemoteBranch extends GitBranch {

  protected GitRemoteBranch(@NotNull String name) {
    super(name);
  }

  /**
   * Returns the name of this remote branch to be used in remote operations: fetch, push, pull.
   * It is the name of this branch how it is defined on the remote.
   * For example, "master".
   * @see #getNameForLocalOperations()
   */
  public abstract @NlsSafe @NotNull String getNameForRemoteOperations();

  /**
   * Returns the name of this remote branch to be used in local operations: checkout, merge, rebase.
   * It is the name of this branch how it is references in this local repository.
   * For example, "origin/master".
   */
  public abstract @NotNull String getNameForLocalOperations();

  public abstract @NotNull GitRemote getRemote();

  @Override
  public boolean isRemote() {
    return true;
  }

}
