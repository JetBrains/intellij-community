// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.tree;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * @author Sergey.Malenkov
 */
public abstract class AbstractTreeModel implements Disposable, TreeModel {
  protected final TreeModelListenerList listeners = new TreeModelListenerList();
  protected volatile boolean disposed;

  @Override
  public void dispose() {
    disposed = true;
    listeners.clear();
  }

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
  public void addTreeModelListener(@NotNull TreeModelListener listener) {
    if (!disposed) listeners.add(listener);
  }

  /**
   * Removes a previously added listener.
   *
   * @param listener a listener to remove
   */
  @Override
  public void removeTreeModelListener(@NotNull TreeModelListener listener) {
    if (!disposed) listeners.remove(listener);
  }

  /**
   * @param path  the path to the node that the user has altered
   * @param value the new value from the tree cell editor
   * @see javax.swing.tree.DefaultTreeModel#valueForPathChanged
   */
  @Override
  public void valueForPathChanged(TreePath path, Object value) {
    throw new UnsupportedOperationException("editable tree have to implement TreeModel#valueForPathChanged");
  }
}
