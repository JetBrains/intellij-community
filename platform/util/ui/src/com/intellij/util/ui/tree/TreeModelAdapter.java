// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.tree;

import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;

public abstract class TreeModelAdapter implements TreeModelListener {

  public enum EventType {StructureChanged, NodesChanged, NodesInserted, NodesRemoved}

  public static @NotNull TreeModelListener create(final @NotNull PairConsumer<? super TreeModelEvent, ? super EventType> consumer) {
    return new TreeModelAdapter() {
      @Override
      protected void process(@NotNull TreeModelEvent event, @NotNull EventType type) {
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
  protected void process(@NotNull TreeModelEvent event, @NotNull EventType type) {
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
