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
import java.util.ArrayDeque;

/**
 * @author Sergey.Malenkov
 */
public final class TreeModelListenerList implements TreeModelListener {
  private static final TreeModelListener[] EMPTY_ARRAY = new TreeModelListener[0];
  private final ArrayDeque<TreeModelListener> myDeque = new ArrayDeque<TreeModelListener>();
  private volatile boolean myDequeEmpty = true;

  /**
   * Adds a listener for changes in a tree model.
   * This method is safe for use by multiple concurrent threads.
   *
   * @param listener a listener to add
   */
  public void add(TreeModelListener listener) {
    if (listener != null) {
      synchronized (myDeque) {
        myDeque.addFirst(listener);
        myDequeEmpty = myDeque.isEmpty();
      }
    }
  }

  /**
   * Removes a previously added listener.
   * This method is safe for use by multiple concurrent threads.
   *
   * @param listener a listener to remove
   */
  public void remove(TreeModelListener listener) {
    if (listener != null) {
      synchronized (myDeque) {
        myDeque.remove(listener);
        myDequeEmpty = myDeque.isEmpty();
      }
    }
  }

  /**
   * Returns {@code true} if no listeners have been added.
   * This method is safe for use by multiple concurrent threads.
   *
   * @return {@code true} if no listeners have been added, or {@code false} otherwise
   */
  public boolean isEmpty() {
    return myDequeEmpty;
  }

  /**
   * Returns all added listeners or an empty array if no listeners have been added.
   * This method is safe for use by multiple concurrent threads.
   *
   * @return all added listeners
   */
  public TreeModelListener[] get() {
    if (myDequeEmpty) return EMPTY_ARRAY;
    synchronized (myDeque) {
      return myDeque.toArray(EMPTY_ARRAY);
    }
  }

  /**
   * Notifies all added listeners that a tree hierarchy was changed.
   *
   * @param event the event object specifying a node with changed hierarchy
   */
  @Override
  public void treeStructureChanged(TreeModelEvent event) {
    for (TreeModelListener listener : get()) {
      listener.treeStructureChanged(event);
    }
  }

  /**
   * Notifies all added listeners that some nodes were changed.
   *
   * @param event the event object specifying changed nodes
   */
  @Override
  public void treeNodesChanged(TreeModelEvent event) {
    for (TreeModelListener listener : get()) {
      listener.treeNodesChanged(event);
    }
  }

  /**
   * Notifies all added listeners that some nodes were inserted.
   *
   * @param event the event object specifying inserted nodes
   */
  @Override
  public void treeNodesInserted(TreeModelEvent event) {
    for (TreeModelListener listener : get()) {
      listener.treeNodesInserted(event);
    }
  }

  /**
   * Notifies all added listeners that some nodes were removed.
   *
   * @param event the event object specifying removed nodes
   */
  @Override
  public void treeNodesRemoved(TreeModelEvent event) {
    for (TreeModelListener listener : get()) {
      listener.treeNodesRemoved(event);
    }
  }
}
