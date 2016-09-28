/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui.tree;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * @author Sergey.Malenkov
 */
public abstract class AbstractTreeModel implements TreeModel {
  private final TreeModelListenerList listeners = new TreeModelListenerList();

  /**
   * Notifies all added listeners that a tree hierarchy was changed.
   *
   * @param path     the path to the parent of the modified items
   * @param indices  index values of the modified items
   * @param children an array containing the inserted, removed, or changed objects
   * @see TreeModelListener#treeStructureChanged(TreeModelEvent)
   */
  protected void treeStructureChanged(TreePath path, int[] indices, Object[] children) {
    if (!listeners.isEmpty()) listeners.treeStructureChanged(new TreeModelEvent(this, path, indices, children));
  }

  /**
   * Notifies all added listeners that some nodes were changed.
   *
   * @param path     the path to the parent of the modified items
   * @param indices  index values of the modified items
   * @param children an array containing the inserted, removed, or changed objects
   * @see TreeModelListener#treeNodesChanged(TreeModelEvent)
   */
  protected void treeNodesChanged(TreePath path, int[] indices, Object[] children) {
    if (!listeners.isEmpty()) listeners.treeNodesChanged(new TreeModelEvent(this, path, indices, children));
  }

  /**
   * Notifies all added listeners that some nodes were inserted.
   *
   * @param path     the path to the parent of the modified items
   * @param indices  index values of the modified items
   * @param children an array containing the inserted, removed, or changed objects
   * @see TreeModelListener#treeNodesInserted(TreeModelEvent)
   */
  protected void treeNodesInserted(TreePath path, int[] indices, Object[] children) {
    if (!listeners.isEmpty()) listeners.treeNodesInserted(new TreeModelEvent(this, path, indices, children));
  }

  /**
   * /**
   * Notifies all added listeners that some nodes were removed.
   *
   * @param path     the path to the parent of the modified items
   * @param indices  index values of the modified items
   * @param children an array containing the inserted, removed, or changed objects
   * @see TreeModelListener#treeNodesRemoved(TreeModelEvent)
   */
  protected void treeNodesRemoved(TreePath path, int[] indices, Object[] children) {
    if (!listeners.isEmpty()) listeners.treeNodesRemoved(new TreeModelEvent(this, path, indices, children));
  }

  /**
   * Adds a listener for changes in a tree model.
   *
   * @param listener a listener to add
   */
  @Override
  public void addTreeModelListener(TreeModelListener listener) {
    listeners.add(listener);
  }

  /**
   * Removes a previously added listener.
   *
   * @param listener a listener to remove
   */
  @Override
  public void removeTreeModelListener(TreeModelListener listener) {
    listeners.remove(listener);
  }
}
