// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import javax.swing.tree.TreePath;

@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
@SuppressWarnings("MissingDeprecatedAnnotation")
public interface Navigatable {
  @NotNull
  Promise<TreePath> nextTreePath(@NotNull TreePath path, Object object);

  @NotNull
  Promise<TreePath> prevTreePath(@NotNull TreePath path, Object object);
}
