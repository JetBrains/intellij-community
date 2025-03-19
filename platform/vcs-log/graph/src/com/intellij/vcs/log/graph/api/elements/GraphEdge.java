// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.api.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class GraphEdge implements GraphElement {
  public static GraphEdge createNormalEdge(int nodeIndex1, int nodeIndex2, @NotNull GraphEdgeType type) {
    assert type.isNormalEdge() : "Unexpected edge type: " + type;
    return new GraphEdge(Math.min(nodeIndex1, nodeIndex2), Math.max(nodeIndex1, nodeIndex2), null, type);
  }

  public static GraphEdge createEdgeWithTargetId(int nodeIndex, @Nullable Integer targetId, @NotNull GraphEdgeType type) {
    return switch (type) {
      case DOTTED_ARROW_UP -> new GraphEdge(null, nodeIndex, targetId, type);
      case NOT_LOAD_COMMIT, DOTTED_ARROW_DOWN -> new GraphEdge(nodeIndex, null, targetId, type);
      default -> throw new AssertionError("Unexpected edge type: " + type);
    };
  }

  private final @Nullable Integer myUpNodeIndex;
  private final @Nullable Integer myDownNodeIndex;
  private final @Nullable Integer myTargetId;
  private final @NotNull GraphEdgeType myType;

  public GraphEdge(@Nullable Integer upNodeIndex,
                   @Nullable Integer downNodeIndex,
                   @Nullable Integer targetId,
                   @NotNull GraphEdgeType type) {
    myUpNodeIndex = upNodeIndex;
    myDownNodeIndex = downNodeIndex;
    myTargetId = targetId;
    myType = type;
  }

  public @Nullable Integer getUpNodeIndex() {
    return myUpNodeIndex;
  }

  public @Nullable Integer getDownNodeIndex() {
    return myDownNodeIndex;
  }

  public @Nullable Integer getTargetId() {
    return myTargetId;
  }

  public @NotNull GraphEdgeType getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GraphEdge graphEdge = (GraphEdge)o;

    if (myType != graphEdge.myType) return false;
    if (!Objects.equals(myUpNodeIndex, graphEdge.myUpNodeIndex)) return false;
    if (!Objects.equals(myDownNodeIndex, graphEdge.myDownNodeIndex)) return false;
    if (!Objects.equals(myTargetId, graphEdge.myTargetId)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myUpNodeIndex != null ? myUpNodeIndex.hashCode() : 0;
    result = 31 * result + (myDownNodeIndex != null ? myDownNodeIndex.hashCode() : 0);
    result = 31 * result + (myTargetId != null ? myTargetId.hashCode() : 0);
    result = 31 * result + myType.hashCode();
    return result;
  }
}
