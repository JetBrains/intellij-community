// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.api.elements;

import org.jetbrains.annotations.NotNull;

public final class GraphNode implements GraphElement {
  private final int myNodeIndex;
  private final @NotNull GraphNodeType myType;

  public GraphNode(int nodeIndex) {
    this(nodeIndex, GraphNodeType.USUAL);
  }

  public GraphNode(int nodeIndex, @NotNull GraphNodeType type) {
    myNodeIndex = nodeIndex;
    myType = type;
  }

  public int getNodeIndex() {
    return myNodeIndex;
  }

  public @NotNull GraphNodeType getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GraphNode graphNode = (GraphNode)o;

    if (myNodeIndex != graphNode.myNodeIndex) return false;
    if (myType != graphNode.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myNodeIndex;
    result = 31 * result + myType.hashCode();
    return result;
  }
}
