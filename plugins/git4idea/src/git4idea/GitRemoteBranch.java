// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NlsSafe
  @NotNull
  public abstract String getNameForRemoteOperations();

  /**
   * Returns the name of this remote branch to be used in local operations: checkout, merge, rebase.
   * It is the name of this branch how it is references in this local repository.
   * For example, "origin/master".
   */
  @NotNull
  public abstract String getNameForLocalOperations();

  @NotNull
  public abstract GitRemote getRemote();

  @Override
  public boolean isRemote() {
    return true;
  }

}
