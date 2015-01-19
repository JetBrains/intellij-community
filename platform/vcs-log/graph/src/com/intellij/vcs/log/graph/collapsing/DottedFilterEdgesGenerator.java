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
package com.intellij.vcs.log.graph.collapsing;

import com.intellij.util.SmartList;
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.api.LiteLinearGraph.NodeFilter;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.vcs.log.graph.api.elements.GraphEdgeType.*;
import static com.intellij.vcs.log.graph.collapsing.GraphAdditionalEdges.NULL_ID;

public class DottedFilterEdgesGenerator {
  public static void update(@NotNull CollapsedGraph collapsedGraph, int upDelegateNodeIndex, int downDelegateNodeIndex) {
    new DottedFilterEdgesGenerator(collapsedGraph, upDelegateNodeIndex, downDelegateNodeIndex).update();
  }

  @NotNull private final CollapsedGraph myCollapsedGraph;

  @NotNull private final LiteLinearGraph myLiteDelegateGraph;

  private final int myUpIndex;
  private final int myDownIndex;
  @NotNull private final ShiftNumber myNumbers;

  private DottedFilterEdgesGenerator(@NotNull CollapsedGraph collapsedGraph, int upIndex, int downIndex) {
    myCollapsedGraph = collapsedGraph;
    myLiteDelegateGraph = LinearGraphUtils.asLiteLinearGraph(collapsedGraph.getDelegateGraph());
    myUpIndex = upIndex;
    myDownIndex = downIndex;
    myNumbers = new ShiftNumber(upIndex, downIndex);
  }

  private boolean nodeIsVisible(int nodeIndex) {
    return myCollapsedGraph.getNodeVisibility(getNodeId(nodeIndex));
  }

  private int getNodeId(int nodeIndex) {
    return myCollapsedGraph.getDelegateGraph().getGraphNode(nodeIndex).getNodeId();
  }

  private void addDottedEdge(int nodeIndex1, int nodeIndex2) {
    myCollapsedGraph.getGraphAdditionalEdges().createEdge(getNodeId(nodeIndex1), getNodeId(nodeIndex2), DOTTED);
  }

  private void addDottedArrow(int nodeIndex, boolean toUp) {
    myCollapsedGraph.getGraphAdditionalEdges().createEdge(getNodeId(nodeIndex), NULL_ID, toUp ? DOTTED_ARROW_UP : DOTTED_ARROW_DOWN);
  }

  // update specified range
  private void update() {
    downWalk();
    upWalk();
  }

  private boolean hasDottedEdges(int nodeIndex, boolean toUp) {
    SmartList<GraphEdge> additionalEdges = new SmartList<GraphEdge>();
    myCollapsedGraph.getGraphAdditionalEdges().appendAdditionalEdges(additionalEdges, getNodeId(nodeIndex));
    for (GraphEdge edge : additionalEdges) {
      Integer anotherNodeIndex;
      if (toUp) anotherNodeIndex = edge.getDownNodeIndex(); else anotherNodeIndex = edge.getUpNodeIndex();

      if (anotherNodeIndex != null && nodeIndex == anotherNodeIndex) {
        return true;
      }
    }
    return false;
  }

  private void addEdgeOrArrow(int currentNodeIndex, int anotherNodeIndex, boolean toUp) {
    if (hasDottedEdges(currentNodeIndex, toUp)) {
      if (nodeIsVisible(anotherNodeIndex)) {
        addDottedEdge(currentNodeIndex, anotherNodeIndex);
      }
      else {
        addDottedArrow(currentNodeIndex, toUp);
      }
    }
  }

  private void downWalk() {
    for (int currentNodeIndex = myUpIndex; currentNodeIndex <= myDownIndex; currentNodeIndex++) {
      if (nodeIsVisible(currentNodeIndex)) {
        int nearlyUp = Integer.MIN_VALUE;
        int maxAdjNumber = Integer.MIN_VALUE;
        for (int upNode : myLiteDelegateGraph.getNodes(currentNodeIndex, NodeFilter.UP)) {
          if (upNode < myUpIndex) {
            addEdgeOrArrow(currentNodeIndex, upNode, true);
            continue;
          }

          if (nodeIsVisible(upNode))
            maxAdjNumber = Math.max(maxAdjNumber, myNumbers.getNumber(upNode));
          else
            nearlyUp = Math.max(nearlyUp, myNumbers.getNumber(upNode));
        }

        if (nearlyUp == maxAdjNumber || nearlyUp == Integer.MIN_VALUE) {
          myNumbers.setNumber(currentNodeIndex, maxAdjNumber);
        } else {
          addDottedEdge(currentNodeIndex, nearlyUp);
          myNumbers.setNumber(currentNodeIndex, nearlyUp);
        }
      } else {
        // node currentNodeIndex invisible

        int nearlyUp = Integer.MIN_VALUE;
        for (int upNode : myLiteDelegateGraph.getNodes(currentNodeIndex, NodeFilter.UP)) {
          if (nodeIsVisible(upNode)) {
            nearlyUp = Math.max(nearlyUp, upNode);
          }
          else {
            if (upNode >= myUpIndex) nearlyUp = Math.max(nearlyUp, myNumbers.getNumber(upNode));
          }
        }
        myNumbers.setNumber(currentNodeIndex, nearlyUp);
      }
    }
  }

  private void upWalk() {
    for (int currentNodeIndex = myDownIndex; currentNodeIndex >= myUpIndex; currentNodeIndex--) {
      if (nodeIsVisible(currentNodeIndex)) {
        int nearlyDown = Integer.MAX_VALUE;
        int minAdjNumber = Integer.MAX_VALUE;
        for (int downNode : myLiteDelegateGraph.getNodes(currentNodeIndex, NodeFilter.DOWN)) {
          if (downNode > myDownIndex) {
            addEdgeOrArrow(currentNodeIndex, downNode, false);
            continue;
          }

          if (nodeIsVisible(downNode))
            minAdjNumber = Math.min(minAdjNumber, myNumbers.getNumber(downNode));
          else
            nearlyDown = Math.min(nearlyDown, myNumbers.getNumber(downNode));
        }

        if (nearlyDown == minAdjNumber || nearlyDown == Integer.MAX_VALUE) {
          myNumbers.setNumber(currentNodeIndex, minAdjNumber);
        } else {
          addDottedEdge(currentNodeIndex, nearlyDown);
          myNumbers.setNumber(currentNodeIndex, nearlyDown);
        }
      } else {
        // node currentNodeIndex invisible

        int nearlyDown = Integer.MAX_VALUE;
        for (int downNode : myLiteDelegateGraph.getNodes(currentNodeIndex, NodeFilter.DOWN)) {
          if (nodeIsVisible(downNode)) {
            nearlyDown = Math.min(nearlyDown, downNode);
          }
          else {
            if (downNode > myDownIndex) nearlyDown = Math.min(nearlyDown, myNumbers.getNumber(downNode));
          }
        }
        myNumbers.setNumber(currentNodeIndex, nearlyDown);
      }
    }
  }



  static class ShiftNumber {
    private final int startIndex;
    private final int endIndex;
    private final int[] numbers;

    ShiftNumber(int startIndex, int endIndex) {
      this.startIndex = startIndex;
      this.endIndex = endIndex;
      numbers = new int[endIndex - startIndex + 1];
    }

    private boolean inRange(int nodeIndex) {
      return startIndex <= nodeIndex && nodeIndex <= endIndex;
    }

    protected int getNumber(int nodeIndex) {
      if (inRange(nodeIndex)) return numbers[nodeIndex - startIndex];

      return -1;
    }

    protected void setNumber(int nodeIndex, int value) {
      if (inRange(nodeIndex)) {
        numbers[nodeIndex - startIndex] = value;
      }
    }
  }

}
