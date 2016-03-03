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

import com.intellij.vcs.log.graph.api.EdgeFilter;
import com.intellij.vcs.log.graph.api.LiteLinearGraph;
import com.intellij.vcs.log.graph.api.LiteLinearGraph.NodeFilter;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.vcs.log.graph.api.elements.GraphEdgeType.*;

public class DottedFilterEdgesGenerator {
  public static void update(@NotNull CollapsedGraph collapsedGraph, int upDelegateNodeIndex, int downDelegateNodeIndex) {
    CollapsedGraph.Modification modification = collapsedGraph.startModification();
    new DottedFilterEdgesGenerator(collapsedGraph, modification, upDelegateNodeIndex, downDelegateNodeIndex).update();
    modification.apply();
  }

  @NotNull private final CollapsedGraph myCollapsedGraph;
  @NotNull private final CollapsedGraph.Modification myModification;

  @NotNull private final LiteLinearGraph myLiteDelegateGraph;

  private final int myUpIndex;
  private final int myDownIndex;
  @NotNull private final ShiftNumber myNumbers;

  private DottedFilterEdgesGenerator(@NotNull CollapsedGraph collapsedGraph,
                                     @NotNull CollapsedGraph.Modification modification,
                                     int upIndex,
                                     int downIndex) {
    myCollapsedGraph = collapsedGraph;
    myModification = modification;
    myLiteDelegateGraph = LinearGraphUtils.asLiteLinearGraph(collapsedGraph.getDelegatedGraph());
    myUpIndex = upIndex;
    myDownIndex = downIndex;
    myNumbers = new ShiftNumber(upIndex, downIndex);
  }

  private boolean nodeIsVisible(int nodeIndex) {
    return myCollapsedGraph.isNodeVisible(nodeIndex);
  }

  private void addDottedEdge(int nodeIndex1, int nodeIndex2) {
    myModification.createEdge(new GraphEdge(nodeIndex1, nodeIndex2, null, DOTTED));
  }

  private void addDottedArrow(int nodeIndex, boolean isUp) {
    myModification.createEdge(new GraphEdge(nodeIndex, null, null, isUp ? DOTTED_ARROW_UP : DOTTED_ARROW_DOWN));
  }

  // update specified range
  private void update() {
    downWalk();
    cleanup();
    upWalk();
  }

  private void cleanup() {
    for (int currentNodeIndex = myUpIndex; currentNodeIndex <= myDownIndex; currentNodeIndex++) {
      myNumbers.setNumber(currentNodeIndex, Integer.MAX_VALUE);
    }
  }

  private boolean hasDottedEdges(int nodeIndex, boolean isUp) {
    for (GraphEdge edge : myModification.getEdgesToAdd().getAdjacentEdges(nodeIndex, EdgeFilter.NORMAL_ALL)) {
      if (edge.getType() == DOTTED) {
        if (isUp && LinearGraphUtils.isEdgeUp(edge, nodeIndex)) return true;
        if (!isUp && LinearGraphUtils.isEdgeDown(edge, nodeIndex)) return false;
      }
    }
    return false;
  }

  private void addEdgeOrArrow(int currentNodeIndex, int anotherNodeIndex, boolean isUp) {
    if (hasDottedEdges(currentNodeIndex, isUp)) {
      if (nodeIsVisible(anotherNodeIndex)) {
        addDottedEdge(currentNodeIndex, anotherNodeIndex);
      }
      else {
        addDottedArrow(currentNodeIndex, isUp);
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

          if (nodeIsVisible(upNode)) {
            maxAdjNumber = Math.max(maxAdjNumber, myNumbers.getNumber(upNode));
          }
          else {
            nearlyUp = Math.max(nearlyUp, myNumbers.getNumber(upNode));
          }
        }

        if (nearlyUp == maxAdjNumber || nearlyUp == Integer.MIN_VALUE) {
          myNumbers.setNumber(currentNodeIndex, maxAdjNumber);
        }
        else {
          addDottedEdge(currentNodeIndex, nearlyUp);
          myNumbers.setNumber(currentNodeIndex, nearlyUp);
        }
      }
      else {
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

          if (nodeIsVisible(downNode)) {
            minAdjNumber = Math.min(minAdjNumber, myNumbers.getNumber(downNode));
          }
          else {
            nearlyDown = Math.min(nearlyDown, myNumbers.getNumber(downNode));
          }
        }

        if (nearlyDown == minAdjNumber || nearlyDown == Integer.MAX_VALUE) {
          myNumbers.setNumber(currentNodeIndex, minAdjNumber);
        }
        else {
          addDottedEdge(currentNodeIndex, nearlyDown);
          myNumbers.setNumber(currentNodeIndex, nearlyDown);
        }
      }
      else {
        // node currentNodeIndex invisible

        int nearlyDown = Integer.MAX_VALUE;
        for (int downNode : myLiteDelegateGraph.getNodes(currentNodeIndex, NodeFilter.DOWN)) {
          if (nodeIsVisible(downNode)) {
            nearlyDown = Math.min(nearlyDown, downNode);
          }
          else {
            if (downNode <= myDownIndex) nearlyDown = Math.min(nearlyDown, myNumbers.getNumber(downNode));
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
