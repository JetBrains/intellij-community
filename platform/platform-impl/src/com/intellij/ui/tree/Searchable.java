// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import javax.swing.tree.TreePath;

public interface Searchable {
  /**
   * Starts searching by the specified object.
   *
   * @param object an object to identify a tree path
   * @return a {@code Promise} containing a search result
   * @see Identifiable#getUniqueID
   */
  @NotNull
  Promise<TreePath> getTreePath(Object object);
}
