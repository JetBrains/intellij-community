// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public interface CommitExecutor {
  @Nls
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

  @NotNull
  CommitSession createCommitSession();
}
