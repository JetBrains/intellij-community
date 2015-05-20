/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.vcs.log.graph.api.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GraphEdge implements GraphElement {
  public static GraphEdge createNormalEdge(int nodeIndex1, int nodeIndex2, @NotNull GraphEdgeType type) {
    assert type.isNormalEdge() : "Unexpected edge type: " + type;
    return new GraphEdge(Math.min(nodeIndex1, nodeIndex2), Math.max(nodeIndex1, nodeIndex2), null, type);
  }

  public static GraphEdge createEdgeWithTargetId(int nodeIndex, @Nullable Integer targetId, @NotNull GraphEdgeType type) {
    switch (type) {
      case DOTTED_ARROW_UP:
        return new GraphEdge(null, nodeIndex, targetId, type);
      case NOT_LOAD_COMMIT:
      case DOTTED_ARROW_DOWN:
        return new GraphEdge(nodeIndex, null, targetId, type);

      default:
        throw new AssertionError("Unexpected edge type: " + type);
    }
  }

  @Nullable private final Integer myUpNodeIndex;
  @Nullable private final Integer myDownNodeIndex;
  @Nullable private final Integer myTargetId;
  @NotNull private final GraphEdgeType myType;

  public GraphEdge(@Nullable Integer upNodeIndex,
                   @Nullable Integer downNodeIndex,
                   @Nullable Integer targetId,
                   @NotNull GraphEdgeType type) {
    myUpNodeIndex = upNodeIndex;
    myDownNodeIndex = downNodeIndex;
    myTargetId = targetId;
    myType = type;
  }

  @Nullable
  public Integer getUpNodeIndex() {
    return myUpNodeIndex;
  }

  @Nullable
  public Integer getDownNodeIndex() {
    return myDownNodeIndex;
  }

  @Nullable
  public Integer getTargetId() {
    return myTargetId;
  }

  @NotNull
  public GraphEdgeType getType() {
    return myType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GraphEdge graphEdge = (GraphEdge)o;

    if (myType != graphEdge.myType) return false;
    if (myUpNodeIndex != null ? !myUpNodeIndex.equals(graphEdge.myUpNodeIndex) : graphEdge.myUpNodeIndex != null) return false;
    if (myDownNodeIndex != null ? !myDownNodeIndex.equals(graphEdge.myDownNodeIndex) : graphEdge.myDownNodeIndex != null) return false;
    if (myTargetId != null ? !myTargetId.equals(graphEdge.myTargetId) : graphEdge.myTargetId != null) return false;

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
