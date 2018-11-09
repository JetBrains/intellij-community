// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.branch;

import org.jetbrains.annotations.Nullable;

/**
 * Represents information about single branch in certain vcs root.
 * Should be used in cases where branches in vcs root have any relationships between each other.
 * <p>
 * For instance, it could be used for branches in Git repositories to also specify tracking branch information.
 */
public interface LinkedBranchData extends BranchData {
  @Nullable
  String getLinkedBranchName();
}
