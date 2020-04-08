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

import com.intellij.vcs.log.graph.GraphColorManager;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import com.intellij.vcs.log.graph.utils.NormalEdge;
import org.jetbrains.annotations.NotNull;

public class ColorGetterByLayoutIndex<CommitId> {
  @NotNull private final LinearGraph myLinearGraph;
  @NotNull private final PermanentGraphInfo<CommitId> myPermanentGraphInfo;
  @NotNull private final GraphColorManager<CommitId> myColorManager;

  public ColorGetterByLayoutIndex(@NotNull LinearGraph linearGraph,
                                  @NotNull PermanentGraphInfo<CommitId> permanentGraphInfo,
                                  @NotNull GraphColorManager<CommitId> colorManager) {
    myLinearGraph = linearGraph;
    myPermanentGraphInfo = permanentGraphInfo;
    myColorManager = colorManager;
  }

  public int getColorId(@NotNull GraphElement element) {
    if (element instanceof GraphNode) {
      int nodeId = myLinearGraph.getNodeId(((GraphNode)element).getNodeIndex());
      return getNodeColor(nodeId, getLayoutIndex(nodeId));
    }
    else {
      GraphEdge edge = (GraphEdge)element;
      NormalEdge normalEdge = LinearGraphUtils.asNormalEdge(edge);
      if (normalEdge == null) {
        int nodeId = myLinearGraph.getNodeId(LinearGraphUtils.getNotNullNodeIndex(edge));
        return getNodeColor(nodeId, getLayoutIndex(nodeId));
      }

      int upNodeId = myLinearGraph.getNodeId(normalEdge.up);
      int downNodeId = myLinearGraph.getNodeId(normalEdge.down);
      int upLayoutIndex = getLayoutIndex(upNodeId);
      int downLayoutIndex = getLayoutIndex(downNodeId);

      if (upLayoutIndex >= downLayoutIndex) {
        return getNodeColor(upNodeId, upLayoutIndex);
      }

      return getNodeColor(downNodeId, downLayoutIndex);
    }
  }

  private int getNodeColor(int nodeId, int layoutIndex) {
    int headNodeId = getHeadNodeId(nodeId);
    CommitId headCommitId = myPermanentGraphInfo.getPermanentCommitsInfo().getCommitId(headNodeId);
    if (layoutIndex == myPermanentGraphInfo.getPermanentGraphLayout().getLayoutIndex(headNodeId)) {
      return myColorManager.getColorOfBranch(headCommitId);
    }
    else {
      return myColorManager.getColorOfFragment(headCommitId, layoutIndex);
    }
  }

  private int getHeadNodeId(int nodeId) {
    if (nodeId < 0) return 0;
    return myPermanentGraphInfo.getPermanentGraphLayout().getOneOfHeadNodeIndex(nodeId);
  }

  private int getLayoutIndex(int nodeId) {
    if (nodeId < 0) return nodeId;
    return myPermanentGraphInfo.getPermanentGraphLayout().getLayoutIndex(nodeId);
  }
}
