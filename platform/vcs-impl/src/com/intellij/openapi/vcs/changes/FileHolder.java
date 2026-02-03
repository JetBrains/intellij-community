// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.AbstractVcs;
import org.jetbrains.annotations.NotNull;

public interface FileHolder {
  /**
   * Notify that CLM refresh has started, everything is dirty
   */
  void cleanAll();

  /**
   * Notify that CLM refresh has started for particular scope
   */
  void cleanUnderScope(@NotNull VcsDirtyScope scope);

  FileHolder copy();

  default void notifyVcsStarted(@NotNull AbstractVcs vcs) {
  }
}
