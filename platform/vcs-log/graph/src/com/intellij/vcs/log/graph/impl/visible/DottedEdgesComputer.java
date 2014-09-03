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
package com.intellij.vcs.log.graph.impl.visible;

import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.utils.Flags;
import org.jetbrains.annotations.NotNull;

public class DottedEdgesComputer {
  @NotNull
  public static MultiMap<Integer, Integer> compute(@NotNull LinearGraph delegateGraph, @NotNull Flags visibleNodes) {
    DottedEdgesComputer dottedEdgesComputer = new DottedEdgesComputer(delegateGraph, visibleNodes);
    dottedEdgesComputer.compute();
    return dottedEdgesComputer.myDottedEdges;
  }

  @NotNull
  private final LinearGraph myDelegateGraph;

  @NotNull
  private final Flags myVisibleNodes;

  @NotNull
  private final MultiMap<Integer, Integer> myDottedEdges;

  @NotNull
  private final int[] myNumbers;

  private DottedEdgesComputer(@NotNull LinearGraph delegateGraph, @NotNull Flags visibleNodes) {
    assert delegateGraph.nodesCount() == visibleNodes.size();
    myDelegateGraph = delegateGraph;
    myVisibleNodes = visibleNodes;
    myDottedEdges = MultiMap.create();
    myNumbers = new int[myDelegateGraph.nodesCount()];
  }

  private void putEdge(int node1, int node2) {
    myDottedEdges.putValue(node1, node2);
    myDottedEdges.putValue(node2, node1);
  }

  private void compute() {
    downWalk();
    upWalk();
  }

  private void downWalk() {
    for (int i = 0; i < myDelegateGraph.nodesCount() - 1; i++) {
      if (myVisibleNodes.get(i)) {
        int nearlyUp = Integer.MIN_VALUE;
        int maxAdjNumber = Integer.MIN_VALUE;
        for (int upNode : myDelegateGraph.getUpNodes(i)) {
          if (myVisibleNodes.get(upNode))
            maxAdjNumber = Math.max(maxAdjNumber, myNumbers[upNode]);
          else
            nearlyUp = Math.max(nearlyUp, myNumbers[upNode]);
        }

        if (nearlyUp == maxAdjNumber || nearlyUp == Integer.MIN_VALUE) {
          myNumbers[i] = maxAdjNumber;
        } else {
          putEdge(i, nearlyUp);
          myNumbers[i] = nearlyUp;
        }
      } else {
        // node i invisible

        int nearlyUp = Integer.MIN_VALUE;
        for (int upNode : myDelegateGraph.getUpNodes(i)) {
          if (myVisibleNodes.get(upNode))
            nearlyUp = Math.max(nearlyUp, upNode);
          else
            nearlyUp = Math.max(nearlyUp, myNumbers[upNode]);
        }
        myNumbers[i] = nearlyUp;
      }
    }
  }

  private void upWalk() {
    for (int i = myDelegateGraph.nodesCount() - 1; i >= 0; i--) {
      if (myVisibleNodes.get(i)) {
        int nearlyDown = Integer.MAX_VALUE;
        int minAdjNumber = Integer.MAX_VALUE;
        for (int downNode : myDelegateGraph.getDownNodes(i)) {
          if (downNode == LinearGraph.NOT_LOAD_COMMIT) continue;
          if (myVisibleNodes.get(downNode))
            minAdjNumber = Math.min(minAdjNumber, myNumbers[downNode]);
          else
            nearlyDown = Math.min(nearlyDown, myNumbers[downNode]);
        }

        if (nearlyDown == minAdjNumber || nearlyDown == Integer.MAX_VALUE) {
          myNumbers[i] = minAdjNumber;
        } else {
          putEdge(i, nearlyDown);
          myNumbers[i] = nearlyDown;
        }

      } else {
        // node i invisible

        int nearlyDown = Integer.MAX_VALUE;
        for (int downNode : myDelegateGraph.getDownNodes(i)) {
          if (downNode == LinearGraph.NOT_LOAD_COMMIT) continue;
          if (myVisibleNodes.get(downNode))
            nearlyDown = Math.min(nearlyDown, downNode);
          else
            nearlyDown = Math.min(nearlyDown, myNumbers[downNode]);
        }
        myNumbers[i] = nearlyDown;
      }
    }
  }
}
