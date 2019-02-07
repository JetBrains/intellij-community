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

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import java.util.List;

/**
 * @author Sergey.Malenkov
 */
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
