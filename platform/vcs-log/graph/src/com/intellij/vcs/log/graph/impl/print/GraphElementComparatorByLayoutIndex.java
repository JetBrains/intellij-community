// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.print;

import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.NormalEdge;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.function.Function;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.asNormalEdge;
import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getNotNullNodeIndex;

@ApiStatus.Internal
public class GraphElementComparatorByLayoutIndex implements Comparator<GraphElement> {
  private final @NotNull Function<? super Integer, @NotNull Integer> myLayoutIndexGetter;

  public GraphElementComparatorByLayoutIndex(@NotNull Function<? super Integer, @NotNull Integer> layoutIndexGetter) {
    myLayoutIndexGetter = layoutIndexGetter;
  }

  @Override
  public int compare(@NotNull GraphElement o1, @NotNull GraphElement o2) {
    if (o1 instanceof GraphEdge edge1 && o2 instanceof GraphEdge edge2) {
      NormalEdge normalEdge1 = asNormalEdge(edge1);
      NormalEdge normalEdge2 = asNormalEdge(edge2);
      if (normalEdge1 == null) return -compare2(edge2, new GraphNode(getNotNullNodeIndex(edge1)));
      if (normalEdge2 == null) return compare2(edge1, new GraphNode(getNotNullNodeIndex(edge2)));

      if (normalEdge1.up == normalEdge2.up) {
        if (normalEdge1.down < normalEdge2.down) {
          return -compare2(edge2, new GraphNode(normalEdge1.down));
        }
        else {
          return compare2(edge1, new GraphNode(normalEdge2.down));
        }
      }

      if (normalEdge1.up < normalEdge2.up) {
        return compare2(edge1, new GraphNode(normalEdge2.up));
      }
      else {
        return -compare2(edge2, new GraphNode(normalEdge1.up));
      }
    }

    if (o1 instanceof GraphEdge && o2 instanceof GraphNode) return compare2((GraphEdge)o1, (GraphNode)o2);

    if (o1 instanceof GraphNode && o2 instanceof GraphEdge) return -compare2((GraphEdge)o2, (GraphNode)o1);

    assert false; // both GraphNode
    return 0;
  }

  private int compare2(@NotNull GraphEdge edge, @NotNull GraphNode node) {
    NormalEdge normalEdge = asNormalEdge(edge);
    if (normalEdge == null) {
      return getLayoutIndex(getNotNullNodeIndex(edge)) - getLayoutIndex(node.getNodeIndex());
    }

    int upEdgeLI = getLayoutIndex(normalEdge.up);
    int downEdgeLI = getLayoutIndex(normalEdge.down);

    int nodeLI = getLayoutIndex(node.getNodeIndex());
    if (Math.max(upEdgeLI, downEdgeLI) != nodeLI) {
      return Math.max(upEdgeLI, downEdgeLI) - nodeLI;
    }
    else {
      return normalEdge.up - node.getNodeIndex();
    }
  }

  private int getLayoutIndex(int nodeIndex) {
    return myLayoutIndexGetter.apply(nodeIndex);
  }
}
