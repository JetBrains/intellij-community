// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.branch;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents information about single branch in certain vcs root.
 */
public interface BranchData {
  @NotNull
  String getPresentableRootName();

  @Nullable
  String getBranchName();
}