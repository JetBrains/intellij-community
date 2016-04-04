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
package com.intellij.vcs.log.graph.impl.print;

import com.intellij.util.NotNullFunction;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.NormalEdge;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.asNormalEdge;
import static com.intellij.vcs.log.graph.utils.LinearGraphUtils.getNotNullNodeIndex;

public class GraphElementComparatorByLayoutIndex implements Comparator<GraphElement> {
  @NotNull private final NotNullFunction<Integer, Integer> myLayoutIndexGetter;

  public GraphElementComparatorByLayoutIndex(@NotNull NotNullFunction<Integer, Integer> layoutIndexGetter) {
    myLayoutIndexGetter = layoutIndexGetter;
  }

  @Override
  public int compare(@NotNull GraphElement o1, @NotNull GraphElement o2) {
    if (o1 instanceof GraphEdge && o2 instanceof GraphEdge) {
      GraphEdge edge1 = (GraphEdge)o1;
      GraphEdge edge2 = (GraphEdge)o2;
      NormalEdge normalEdge1 = asNormalEdge(edge1);
      NormalEdge normalEdge2 = asNormalEdge(edge2);
      if (normalEdge1 == null) return -compare2(edge2, new GraphNode(getNotNullNodeIndex(edge1)));
      if (normalEdge2 == null) return compare2(edge1, new GraphNode(getNotNullNodeIndex(edge2)));

      if (normalEdge1.up == normalEdge2.up) {
        if (getLayoutIndex(normalEdge1.down) != getLayoutIndex(normalEdge2.down)) {
          return getLayoutIndex(normalEdge1.down) - getLayoutIndex(normalEdge2.down);
        }
        else {
          return normalEdge1.down - normalEdge2.down;
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
    return myLayoutIndexGetter.fun(nodeIndex);
  }
}
