// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;

import javax.swing.JTree;
import javax.swing.tree.TreePath;

/**
 * @deprecated use {@link TreeUtil#promiseVisit(JTree, TreeVisitor)} instead
 */
@Deprecated(forRemoval = true)
public interface Searchable {
  /**
   * Starts searching by the specified object.
   *
   * @param object an object to identify a tree path
   * @return a {@code Promise} containing a search result
   */
  @NotNull
  Promise<TreePath> getTreePath(Object object);
}
