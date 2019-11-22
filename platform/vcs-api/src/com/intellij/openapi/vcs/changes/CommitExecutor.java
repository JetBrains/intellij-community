// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nls;
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
  default String getId() {
    return null;
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
    CommitSession commitSession = createCommitSession();
    if (commitSession instanceof CommitSessionContextAware) ((CommitSessionContextAware)commitSession).setContext(commitContext);
    return commitSession;
  }
}
