// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ChangesGroupingPolicy {
  @Nullable
  ChangesBrowserNode getParentNodeFor(@NotNull StaticFilePath nodePath, @NotNull ChangesBrowserNode subtreeRoot);

  default void setNextGroupingPolicy(@Nullable ChangesGroupingPolicy policy) {
  }
}