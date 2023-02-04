// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.openapi.util.Comparing;
import com.intellij.vcs.log.graph.GraphColorManager;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.api.printer.PrintElementPresentationManager;
import com.intellij.vcs.log.graph.impl.print.ColorGetterByLayoutIndex;
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

class PrintElementPresentationManagerImpl<CommitId> implements PrintElementPresentationManager {
  @NotNull private final LinearGraph myLinearGraph;
  @NotNull private final ColorGetterByLayoutIndex<CommitId> myColorGetter;
  @NotNull private Selection mySelection = new Selection(Collections.emptySet());

  PrintElementPresentationManagerImpl(@NotNull PermanentGraphInfo<CommitId> permanentGraphInfo,
                                      @NotNull LinearGraph linearGraph,
                                      @NotNull GraphColorManager<CommitId> colorManager) {
    myLinearGraph = linearGraph;
    myColorGetter = new ColorGetterByLayoutIndex<>(linearGraph, permanentGraphInfo, colorManager);
  }

  @Override
  public boolean isSelected(@NotNull PrintElementWithGraphElement printElement) {
    return mySelection.isSelected(printElement);
  }

  boolean setSelectedElement(@NotNull PrintElementWithGraphElement printElement) {
    return setSelection(new Selection(printElement));
  }

  boolean setSelectedElements(@NotNull Set<Integer> selectedNodeId) {
    return setSelection(new Selection(selectedNodeId));
  }

  private boolean setSelection(@NotNull Selection newSelection) {
    if (newSelection.equals(mySelection)) return false;
    mySelection = newSelection;
    return true;
  }

  @Override
  public int getColorId(@NotNull GraphElement element) {
    return myColorGetter.getColorId(element);
  }

  public class Selection {
    @Nullable private final PrintElementWithGraphElement mySelectedPrintElement;
    @NotNull private final Set<Integer> mySelectedNodeIds;

    public Selection(@NotNull Set<Integer> selectedNodeId) {
      this(null, selectedNodeId);
    }

    public Selection(@NotNull PrintElementWithGraphElement printElement) {
      this(printElement, Collections.emptySet());
    }

    private Selection(@Nullable PrintElementWithGraphElement printElement,
                      @NotNull Set<Integer> nodeIds) {
      mySelectedPrintElement = printElement;
      mySelectedNodeIds = nodeIds;
    }

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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Selection selection = (Selection)o;
      return Objects.equals(mySelectedPrintElement, selection.mySelectedPrintElement) &&
             Comparing.haveEqualElements(mySelectedNodeIds, selection.mySelectedNodeIds);
    }

    @Override
    public int hashCode() {
      return 31 * Objects.hashCode(mySelectedPrintElement) + Comparing.unorderedHashcode(mySelectedNodeIds);
    }
  }
}
