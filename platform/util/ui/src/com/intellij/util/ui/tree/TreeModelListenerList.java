// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.tree;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import java.util.List;

public final class TreeModelListenerList implements TreeModelListener {
  private final List<TreeModelListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  /**
   * Adds a listener for changes in a tree model.
   * This method is safe for use by multiple concurrent threads.
   *
   * @param listener a listener to add
   */
  public void add(@NotNull TreeModelListener listener) {
    // in this weird swing world it's customary to fire listeners in the reverse order of their addition
    myListeners.add(0, listener);
  }

  /**
   * Removes a previously added listener.
   * This method is safe for use by multiple concurrent threads.
   *
   * @param listener a listener to remove
   */
  public void remove(@NotNull TreeModelListener listener) {
    myListeners.remove(listener);
  }

  /**
   * Removes all added listeners.
   * This method is safe for use by multiple concurrent threads.
   */
  public void clear() {
    myListeners.clear();
  }

  /**
   * Returns {@code true} if no listeners have been added.
   * This method is safe for use by multiple concurrent threads.
   *
   * @return {@code true} if no listeners have been added, or {@code false} otherwise
   */
  public boolean isEmpty() {
    return myListeners.isEmpty();
  }

  /**
   * Notifies all added listeners that a tree hierarchy was changed.
   *
   * @param event the event object specifying a node with changed hierarchy
   */
  @Override
  public void treeStructureChanged(@NotNull TreeModelEvent event) {
    for (TreeModelListener listener : myListeners) {
      listener.treeStructureChanged(event);
    }
  }

  /**
   * Notifies all added listeners that some nodes were changed.
   *
   * @param event the event object specifying changed nodes
   */
  @Override
  public void treeNodesChanged(@NotNull TreeModelEvent event) {
    for (TreeModelListener listener : myListeners) {
      listener.treeNodesChanged(event);
    }
  }

  /**
   * Notifies all added listeners that some nodes were inserted.
   *
   * @param event the event object specifying inserted nodes
   */
  @Override
  public void treeNodesInserted(@NotNull TreeModelEvent event) {
    for (TreeModelListener listener : myListeners) {
      listener.treeNodesInserted(event);
    }
  }

  /**
   * Notifies all added listeners that some nodes were removed.
   *
   * @param event the event object specifying removed nodes
   */
  @Override
  public void treeNodesRemoved(@NotNull TreeModelEvent event) {
    for (TreeModelListener listener : myListeners) {
      listener.treeNodesRemoved(event);
    }
  }
}
