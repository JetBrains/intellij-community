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
  @Nullable
  private final Integer myUpNodeIndex;
  @Nullable
  private final Integer myDownNodeIndex;
  @Nullable
  private final Integer myAdditionInfo;
  @NotNull
  private final GraphEdgeType myType;

  public GraphEdge(@Nullable Integer upNodeIndex, @Nullable Integer downNodeIndex, @NotNull GraphEdgeType type) {
    this(upNodeIndex, downNodeIndex, null, type);
  }

  public GraphEdge(@Nullable Integer upNodeIndex, @Nullable Integer downNodeIndex, @Nullable Integer additionInfo, @NotNull GraphEdgeType type) {
    myUpNodeIndex = upNodeIndex;
    myDownNodeIndex = downNodeIndex;
    myAdditionInfo = additionInfo;
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
  public Integer getAdditionInfo() {
    return myAdditionInfo;
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
    if (myAdditionInfo != null ? !myAdditionInfo.equals(graphEdge.myAdditionInfo) : graphEdge.myAdditionInfo != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myUpNodeIndex != null ? myUpNodeIndex.hashCode() : 0;
    result = 31 * result + (myDownNodeIndex != null ? myDownNodeIndex.hashCode() : 0);
    result = 31 * result + (myAdditionInfo != null ? myAdditionInfo.hashCode() : 0);
    result = 31 * result + myType.hashCode();
    return result;
  }
}
