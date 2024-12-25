// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.collapsing;

import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.UnsignedBitSet;
import org.jetbrains.annotations.NotNull;

class GraphNodesVisibility {
  private final @NotNull LinearGraph myLinearGraph;
  private @NotNull UnsignedBitSet myNodeVisibilityById;

  GraphNodesVisibility(@NotNull LinearGraph linearGraph, @NotNull UnsignedBitSet nodeVisibilityById) {
    myLinearGraph = linearGraph;
    myNodeVisibilityById = nodeVisibilityById;
  }

  @NotNull
  UnsignedBitSet getNodeVisibilityById() {
    return myNodeVisibilityById;
  }

  void setNodeVisibilityById(@NotNull UnsignedBitSet nodeVisibilityById) {
    myNodeVisibilityById = nodeVisibilityById;
  }

  boolean isVisible(int nodeIndex) {
    return myNodeVisibilityById.get(nodeId(nodeIndex));
  }

  void show(int nodeIndex) {
    myNodeVisibilityById.set(nodeId(nodeIndex), true);
  }

  void hide(int nodeIndex) {
    myNodeVisibilityById.set(nodeId(nodeIndex), false);
  }

  Flags asFlags() {
    return new Flags() {
      @Override
      public int size() {
        return myLinearGraph.nodesCount();
      }

      @Override
      public boolean get(int index) {
        return myNodeVisibilityById.get(nodeId(index));
      }

      @Override
      public void set(int index, boolean value) {
        myNodeVisibilityById.set(nodeId(index), value);
      }

      @Override
      public void setAll(boolean value) {
        for (int index = 0; index < size(); index++) set(index, value);
      }
    };
  }

  private int nodeId(int nodeIndex) {
    return myLinearGraph.getNodeId(nodeIndex);
  }
}
