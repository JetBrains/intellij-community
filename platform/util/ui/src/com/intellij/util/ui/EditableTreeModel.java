// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import javax.swing.tree.TreePath;
import java.util.Collection;

/**
 * @author Konstantin Bulenkov
 */
public interface EditableTreeModel {
  /**
   * Adds a new node into a
   * @param parentOrNeighbour selected node, maybe used as parent or as a neighbour
   * @return path to newly created element
   */
  TreePath addNode(TreePath parentOrNeighbour);

  void removeNode(TreePath path);
  void removeNodes(Collection<? extends TreePath> path);

  void moveNodeTo(TreePath parentOrNeighbour);
}
