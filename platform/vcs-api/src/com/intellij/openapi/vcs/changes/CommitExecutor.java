// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CommitExecutor {
  @Nls
  @NotNull
  String getActionText();

  default boolean useDefaultAction() {
    return true;
  }

  @Nullable
  @NonNls
  default String getId() {
    return null;
  }

  default boolean areChangesRequired() {
    return true;
  }

  default boolean supportsPartialCommit() {
    return false;
  }

  /**
   * @deprecated use {@link #createCommitSession(CommitContext)}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  @NotNull
  default CommitSession createCommitSession() {
    throw new AbstractMethodError();
  }

  @SuppressWarnings("deprecation")
  @NotNull
  default CommitSession createCommitSession(@NotNull CommitContext commitContext) {
    return createCommitSession();
  }
}
