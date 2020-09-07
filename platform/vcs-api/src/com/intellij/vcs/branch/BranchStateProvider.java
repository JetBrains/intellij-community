// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.branch;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BranchStateProvider {
  ExtensionPointName<BranchStateProvider> EP_NAME = ExtensionPointName.create("com.intellij.vcs.branchStateProvider");

  /**
   * Returns information about current branch of the given path.
   */
  @RequiresEdt
  @Nullable
  BranchData getCurrentBranch(@NotNull FilePath path);
}
