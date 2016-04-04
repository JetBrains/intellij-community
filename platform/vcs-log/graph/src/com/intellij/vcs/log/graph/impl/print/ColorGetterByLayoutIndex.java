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

  public ColorGetterByLayoutIndex(@NotNull LinearGraph linearGraph, @NotNull PermanentGraphInfo<CommitId> permanentGraphInfo) {
    myLinearGraph = linearGraph;
    myPermanentGraphInfo = permanentGraphInfo;
  }

  public int getColorId(@NotNull GraphElement element) {
    int upNodeIndex, downNodeIndex;
    if (element instanceof GraphNode) {
      upNodeIndex = ((GraphNode)element).getNodeIndex();
      downNodeIndex = upNodeIndex;
    }
    else {
      GraphEdge edge = (GraphEdge)element;
      NormalEdge normalEdge = LinearGraphUtils.asNormalEdge(edge);
      if (normalEdge != null) {
        upNodeIndex = normalEdge.up;
        downNodeIndex = normalEdge.down;
      }
      else {
        upNodeIndex = LinearGraphUtils.getNotNullNodeIndex(edge);
        downNodeIndex = upNodeIndex;
      }
    }

    int upLayoutIndex = getLayoutIndex(upNodeIndex);
    int downLayoutIndex = getLayoutIndex(downNodeIndex);

    CommitId headCommitId = getOneOfHeads(upNodeIndex);
    GraphColorManager<CommitId> myColorManager = myPermanentGraphInfo.getGraphColorManager();
    if (upLayoutIndex != downLayoutIndex) {
      return myColorManager.getColorOfFragment(headCommitId, Math.max(upLayoutIndex, downLayoutIndex));
    }

    if (upLayoutIndex == myPermanentGraphInfo.getPermanentGraphLayout().getLayoutIndex(getHeadNodeId(upNodeIndex))) {
      return myColorManager.getColorOfBranch(headCommitId);
    }
    else {
      return myColorManager.getColorOfFragment(headCommitId, upLayoutIndex);
    }

  }

  private int getHeadNodeId(int upNodeIndex) {
    int nodeId = getNodeId(upNodeIndex);
    if (nodeId < 0) return 0;
    return myPermanentGraphInfo.getPermanentGraphLayout().getOneOfHeadNodeIndex(nodeId);
  }

  private CommitId getOneOfHeads(int upNodeIndex) {
    return myPermanentGraphInfo.getPermanentCommitsInfo().getCommitId(getHeadNodeId(upNodeIndex));
  }

  private int getLayoutIndex(int upNodeIndex) {
    int nodeId = getNodeId(upNodeIndex);
    if (nodeId < 0) return nodeId;
    return myPermanentGraphInfo.getPermanentGraphLayout().getLayoutIndex(nodeId);
  }

  private int getNodeId(int upNodeIndex) {
    return myLinearGraph.getNodeId(upNodeIndex);
  }

}
