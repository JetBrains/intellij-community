// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree;

import javax.swing.tree.TreeModel;
import java.util.List;

/**
 * This is an extension for the {@link TreeModel} which is supported by {@link AsyncTreeModel}.
 * It is intended to simplify implementing of a couple corresponding methods in a model.
 * Also this interface provides a way to cancel children updating for the specified node.
 * If the underlying model returns {@code null}, the node children in {@link AsyncTreeModel} will not be updated.
 * To update children this model should notify about changed structure by usual means.
 *
 * @see TreeModel#getChildCount(Object)
 * @see TreeModel#getChild(Object, int)
 */
public interface ChildrenProvider<T> {
  /**
   * @param parent a tree node
   * @return all children of the specified parent node or {@code null} if they are not ready yet
   */
  List<? extends T> getChildren(Object parent);
}
