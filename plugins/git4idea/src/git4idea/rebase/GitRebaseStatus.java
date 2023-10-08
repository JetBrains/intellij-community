// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase;

import org.jetbrains.annotations.NotNull;

class GitRebaseStatus {

  enum Type {
    /**
     * Rebase has completed successfully.
     */
    SUCCESS,
    /**
     * Rebase started, and some commits were already applied,
     * but then rebase stopped because of conflicts, or to edit during interactive rebase, or because of an error.<br/>
     * Such rebase can be retried/continued by calling `git rebase --continue/--skip`, or
     * it can be aborted by calling `git rebase --abort`.
     */
    SUSPENDED,
    /**
     * Rebase started, but immediately stopped because of an error at the very beginning.
     * As opposed to {@link #SUSPENDED}, no commits have been applied yet. <br/>
     * Retrying such rebase requires calling `git rebase <all params>` again,
     * there is nothing to abort.
     */
    ERROR,
    /**
     * Rebase hasn't started yet.
     */
    NOT_STARTED
  }

  private final @NotNull Type myType;

  static @NotNull GitRebaseStatus notStarted() {
    return new GitRebaseStatus(Type.NOT_STARTED);
  }

  GitRebaseStatus(@NotNull Type type) {
    myType = type;
  }

  @NotNull
  Type getType() {
    return myType;
  }

  @Override
  public String toString() {
    return myType.toString();
  }
}
