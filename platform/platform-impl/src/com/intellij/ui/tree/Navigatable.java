// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import javax.swing.tree.TreePath;

public interface Navigatable {
  @NotNull
  Promise<TreePath> nextTreePath(@NotNull TreePath path, Object object);

  @NotNull
  Promise<TreePath> prevTreePath(@NotNull TreePath path, Object object);
}
