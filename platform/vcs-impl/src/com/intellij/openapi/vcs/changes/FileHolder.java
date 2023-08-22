// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  void cleanAndAdjustScope(@NotNull VcsModifiableDirtyScope scope);

  FileHolder copy();

  default void notifyVcsStarted(@NotNull AbstractVcs vcs) {
  }
}
