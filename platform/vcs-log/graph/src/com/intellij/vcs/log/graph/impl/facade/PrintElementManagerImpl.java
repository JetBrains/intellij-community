/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.util.NotNullFunction;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.api.printer.PrintElementManager;
import com.intellij.vcs.log.graph.impl.print.ColorGetterByLayoutIndex;
import com.intellij.vcs.log.graph.impl.print.GraphElementComparatorByLayoutIndex;
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

class PrintElementManagerImpl implements PrintElementManager {
  @NotNull private final Comparator<GraphElement> myGraphElementComparator;
  @NotNull private final ColorGetterByLayoutIndex myColorGetter;
  @NotNull private final LinearGraph myLinearGraph;
  @NotNull private Set<Integer> mySelectedNodeIds = Collections.emptySet();
  @Nullable private PrintElementWithGraphElement mySelectedPrintElement = null;

  @SuppressWarnings("unchecked")
  PrintElementManagerImpl(@NotNull final LinearGraph linearGraph, @NotNull final PermanentGraphInfo myPermanentGraph) {
    myLinearGraph = linearGraph;
    myColorGetter = new ColorGetterByLayoutIndex(linearGraph, myPermanentGraph);
    myGraphElementComparator = new GraphElementComparatorByLayoutIndex(new NotNullFunction<Integer, Integer>() {
      @NotNull
      @Override
      public Integer fun(Integer nodeIndex) {
        int nodeId = linearGraph.getNodeId(nodeIndex);
        if (nodeId < 0) return nodeId;
        return myPermanentGraph.getPermanentGraphLayout().getLayoutIndex(nodeId);
      }
    });
  }

  @Override
  public boolean isSelected(@NotNull PrintElementWithGraphElement printElement) {
    if (printElement.equals(mySelectedPrintElement)) return true;

    GraphElement graphElement = printElement.getGraphElement();
    if (graphElement instanceof GraphNode) {
      int nodeId = myLinearGraph.getNodeId(((GraphNode)graphElement).getNodeIndex());
      return mySelectedNodeIds.contains(nodeId);
    }
    if (graphElement instanceof GraphEdge) {
      GraphEdge edge = (GraphEdge)graphElement;
      boolean selected = edge.getTargetId() == null || mySelectedNodeIds.contains(edge.getTargetId());
      selected &= edge.getUpNodeIndex() == null || mySelectedNodeIds.contains(myLinearGraph.getNodeId(edge.getUpNodeIndex()));
      selected &= edge.getDownNodeIndex() == null || mySelectedNodeIds.contains(myLinearGraph.getNodeId(edge.getDownNodeIndex()));
      return selected;
    }

    return false;
  }

  void setSelectedElement(@NotNull PrintElementWithGraphElement printElement) {
    mySelectedNodeIds = Collections.emptySet();
    mySelectedPrintElement = printElement;
  }

  void setSelectedElements(@NotNull Set<Integer> selectedNodeId) {
    mySelectedPrintElement = null;
    mySelectedNodeIds = selectedNodeId;
  }

  @Override
  public int getColorId(@NotNull GraphElement element) {
    return myColorGetter.getColorId(element);
  }

  @NotNull
  @Override
  public Comparator<GraphElement> getGraphElementComparator() {
    return myGraphElementComparator;
  }
}
