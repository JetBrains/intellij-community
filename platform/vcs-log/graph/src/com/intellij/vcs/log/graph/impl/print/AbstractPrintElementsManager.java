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

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.GraphColorManager;
import com.intellij.vcs.log.graph.SimplePrintElement;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LinearGraphWithCommitInfo;
import com.intellij.vcs.log.graph.api.LinearGraphWithElementInfo;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.printer.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.api.printer.PrintElementsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collections;
import java.util.Set;

public abstract class AbstractPrintElementsManager<CommitId> implements PrintElementsManager {

  @Nullable
  public static GraphEdge containedCollapsedEdge(@NotNull GraphElement element, @NotNull LinearGraphWithElementInfo graphWithElementsInfo) {
    if (element instanceof GraphEdge) {
      GraphEdge edge = (GraphEdge)element;
      if (edge.getType() == GraphEdge.Type.HIDE)
        return edge;

    } else {
      int nodeIndex = ((GraphNode)element).getNodeIndex();
      for (int upNode : graphWithElementsInfo.getUpNodes(nodeIndex)) {
        if (graphWithElementsInfo.getEdgeType(upNode, nodeIndex) == GraphEdge.Type.HIDE)
          return new GraphEdge(upNode, nodeIndex, GraphEdge.Type.HIDE);
      }
      for (int downNode : graphWithElementsInfo.getDownNodes(nodeIndex)) {
        if (graphWithElementsInfo.getEdgeType(nodeIndex, downNode) == GraphEdge.Type.HIDE)
          return new GraphEdge(nodeIndex, downNode, GraphEdge.Type.HIDE);
      }
    }

    return null;
  }

  private static final Cursor DEFAULT_CURSOR = Cursor.getDefaultCursor();
  private static final Cursor HAND_CURSOR = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);

  @NotNull
  protected final LinearGraphWithCommitInfo<CommitId> myPrintedLinearGraph;

  @NotNull
  protected final GraphColorManager<CommitId> myColorManager;

  @Nullable
  private PrintElementWithGraphElement mySpecialSelectedPrintElement = null;

  @NotNull
  private Set<Integer> mySelectedNodes = Collections.emptySet();

  protected AbstractPrintElementsManager(@NotNull LinearGraphWithCommitInfo<CommitId> printedLinearGraph, @NotNull GraphColorManager<CommitId> colorManager) {
    myPrintedLinearGraph = printedLinearGraph;
    myColorManager = colorManager;
  }

  @Override
  public boolean elementIsSelected(@NotNull PrintElementWithGraphElement printElement) {
    if (mySpecialSelectedPrintElement != null && printElement.equals(mySpecialSelectedPrintElement))
      return true;

    GraphElement graphElement = printElement.getGraphElement();
    if (graphElement instanceof GraphNode) {
      return mySelectedNodes.contains(((GraphNode)graphElement).getNodeIndex());
    } else {
      GraphEdge edge = (GraphEdge)graphElement;
      return mySelectedNodes.contains(edge.getUpNodeIndex()) && mySelectedNodes.contains(edge.getDownNodeIndex());
    }
  }

  @Nullable
  @Override
  public Cursor performOverElement(@Nullable PrintElementWithGraphElement printElement) {
    if (printElement instanceof SimplePrintElement) {
      SimplePrintElement.Type elementType = ((SimplePrintElement)printElement).getType();
      if (elementType == SimplePrintElement.Type.UP_ARROW || elementType == SimplePrintElement.Type.DOWN_ARROW) {
        mySelectedNodes = Collections.emptySet();
        mySpecialSelectedPrintElement = printElement;
        return HAND_CURSOR;
      }
    } else {
      mySpecialSelectedPrintElement = null;
    }

    if (printElement != null) {
      GraphEdge graphEdge = containedCollapsedEdge(printElement.getGraphElement(), myPrintedLinearGraph);
      if (graphEdge != null) {
        mySelectedNodes = ContainerUtil.set(graphEdge.getUpNodeIndex(), graphEdge.getDownNodeIndex());
      } else {
        mySelectedNodes = getSelectedNodes(printElement.getGraphElement());
      }
    } else {
      mySelectedNodes = Collections.emptySet();
    }

    return DEFAULT_CURSOR; // todo when drop GraphFacade, return null, when mySpecialSelectedPrintElement == null
  }

  @Override
  public int getColorId(@NotNull GraphElement element) {
    int upNodeIndex, downNodeIndex;
    if (element instanceof GraphNode) {
      upNodeIndex = ((GraphNode)element).getNodeIndex();
      downNodeIndex = upNodeIndex;
    } else {
      GraphEdge edge = (GraphEdge)element;
      upNodeIndex = edge.getUpNodeIndex();
      downNodeIndex = edge.getDownNodeIndex();
    }
    if (downNodeIndex == LinearGraph.NOT_LOAD_COMMIT)
      downNodeIndex = upNodeIndex;

    int upLayoutIndex = myPrintedLinearGraph.getLayoutIndex(upNodeIndex);
    int downLayoutIndex = myPrintedLinearGraph.getLayoutIndex(downNodeIndex);

    CommitId headCommitId = myPrintedLinearGraph.getOneOfHeads(upNodeIndex);
    if (upLayoutIndex != downLayoutIndex) {
      return myColorManager.getColorOfFragment(headCommitId, Math.max(upLayoutIndex, downLayoutIndex));
    }

    if (upLayoutIndex == myPrintedLinearGraph.getHeadLayoutIndex(upNodeIndex))
      return myColorManager.getColorOfBranch(headCommitId);
    else
      return myColorManager.getColorOfFragment(headCommitId, upLayoutIndex);
  }

  @NotNull
  protected abstract Set<Integer> getSelectedNodes(@NotNull GraphElement graphElement);
}
