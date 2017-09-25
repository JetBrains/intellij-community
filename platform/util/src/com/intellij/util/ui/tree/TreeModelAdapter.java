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

import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;

/**
 * @author dyoma
 * @author Sergey.Malenkov
 */
public abstract class TreeModelAdapter implements TreeModelListener {
  
  public enum EventType {StructureChanged, NodesChanged, NodesInserted, NodesRemoved}
  
  @NotNull
  public static TreeModelListener create(@NotNull final PairConsumer<TreeModelEvent, EventType> consumer) {
    return new TreeModelAdapter() {
      @Override
      protected void process(TreeModelEvent event, EventType type) {
        consumer.consume(event, type);
      }
    };
  }

  /**
   * Invoked after a tree has changed.
   *
   * @param event the event object specifying changed nodes
   * @param type  the event type specifying a kind of changes
   */
  protected void process(TreeModelEvent event, EventType type) {
  }

  /**
   * Invoked after a tree hierarchy has changed.
   * By default, it calls the {@link #process} method with the corresponding event type.
   *
   * @param event the event object specifying a node with changed hierarchy
   */
  @Override
  public void treeStructureChanged(TreeModelEvent event) {
    if (event != null) process(event, EventType.StructureChanged);
  }

  /**
   * Invoked after some nodes have been changed.
   * By default, it calls the {@link #process} method with the corresponding event type.
   *
   * @param event the event object specifying changed nodes
   */
  @Override
  public void treeNodesChanged(TreeModelEvent event) {
    if (event != null) process(event, EventType.NodesChanged);
  }

  /**
   * Invoked after some nodes have been inserted.
   * By default, it calls the {@link #process} method with the corresponding event type.
   *
   * @param event the event object specifying inserted nodes
   */
  @Override
  public void treeNodesInserted(TreeModelEvent event) {
    if (event != null) process(event, EventType.NodesInserted);
  }

  /**
   * Invoked after some nodes have been removed.
   * By default, it calls the {@link #process} method with the corresponding event type.
   *
   * @param event the event object specifying removed nodes
   */
  @Override
  public void treeNodesRemoved(TreeModelEvent event) {
    if (event != null) process(event, EventType.NodesRemoved);
  }
}
