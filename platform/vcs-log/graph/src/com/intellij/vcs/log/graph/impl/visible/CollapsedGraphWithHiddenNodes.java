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

import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LinearGraphWithHiddenNodes;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.ListenerController;
import com.intellij.vcs.log.graph.utils.UpdatableIntToIntMap;
import com.intellij.vcs.log.graph.utils.impl.SetListenerController;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.vcs.log.newgraph.utils.MyUtils.setAllValues;

public class CollapsedGraphWithHiddenNodes implements LinearGraphWithHiddenNodes {
  @NotNull
  private final LinearGraph myPermanentGraph;

  @NotNull
  private final Flags visibleNodesInBranches;

  @NotNull
  private final Flags visibleNodes;

  @NotNull
  private final UpdatableIntToIntMap intToIntMap;

  @NotNull
  private final DfsUtil myDfsUtil;

  @NotNull
  private final Map<Integer, Pair<Integer, Integer>> upToEdge = new HashMap<Integer, Pair<Integer, Integer>>();

  @NotNull
  private final Map<Integer, Pair<Integer, Integer>> downToEdge = new HashMap<Integer, Pair<Integer, Integer>>();

  @NotNull
  private final SetListenerController<UpdateListener> myListenerController = new SetListenerController<UpdateListener>();

  public CollapsedGraphWithHiddenNodes(@NotNull LinearGraph permanentGraph,
                                       @NotNull Flags visibleNodesInBranches,
                                       @NotNull Flags visibleNodes,
                                       @NotNull UpdatableIntToIntMap intToIntMap,
                                       @NotNull DfsUtil dfsUtil) {
    myPermanentGraph = permanentGraph;
    this.visibleNodesInBranches = visibleNodesInBranches;
    this.visibleNodes = visibleNodes;
    this.intToIntMap = intToIntMap;
    myDfsUtil = dfsUtil;
  }

  // nodes up and down must be loaded
  public void collapse(int upNodeIndex, final int downNodeIndex) {
    Pair<Integer, Integer> edge = Pair.create(upNodeIndex, downNodeIndex);

    myDfsUtil.nodeDfsIterator(upNodeIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        if (upToEdge.containsKey(currentNode)) {
          Pair<Integer, Integer> edge = upToEdge.remove(currentNode);
          downToEdge.remove(edge.second);
          if (edge.second == downNodeIndex)
            return NODE_NOT_FOUND;

          visibleNodes.set(edge.second, false);
          return edge.second;
        }

        for (int downNode : myPermanentGraph.getDownNodes(currentNode)) {
          if (visibleNodes.get(downNode) && downNode != downNodeIndex) {
            visibleNodes.set(downNode, false);
            return downNode;
          }
        }
        return NODE_NOT_FOUND;
      }
    });

    upToEdge.put(upNodeIndex, edge);
    downToEdge.put(downNodeIndex, edge);
    intToIntMap.update(upNodeIndex, downNodeIndex);
  }

  public void expand(int upNodeIndex, final int downNodeIndex) {
    upToEdge.remove(upNodeIndex);
    downToEdge.remove(downNodeIndex);

    myDfsUtil.nodeDfsIterator(upNodeIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        for (int downNode : myPermanentGraph.getDownNodes(currentNode)) {
          if (!visibleNodes.get(downNode) && downNode != downNodeIndex) {
            visibleNodes.set(downNode, true);
            return downNode;
          }
        }
        return NODE_NOT_FOUND;
      }
    });
    intToIntMap.update(upNodeIndex, downNodeIndex);
  }

  public boolean nodeIsVisible(int nodeIndex) {
    if (nodeIndex == LinearGraph.NOT_LOAD_COMMIT)
      return true;
    return visibleNodes.get(nodeIndex) && visibleNodesInBranches.get(nodeIndex);
  }

  public void expandAll() {
    upToEdge.clear();
    downToEdge.clear();
    setAllValues(visibleNodes, true);
    intToIntMap.update(0, myPermanentGraph.nodesCount() - 1);
  }

  @NotNull
  @Override
  public GraphNode.Type getNodeType(int nodeIndex) {
    return GraphNode.Type.USUAL;
  }

  @NotNull
  @Override
  public GraphEdge.Type getEdgeType(int upNodeIndex, int downNodeIndex) {
    if (upToEdge.containsKey(upNodeIndex))
      return GraphEdge.Type.HIDE;
    else
      return GraphEdge.Type.USUAL;
  }

  @NotNull
  @Override
  public ListenerController<UpdateListener> getListenerController() {
    return myListenerController;
  }

  @Override
  public int nodesCount() {
    return intToIntMap.shortSize();
  }

  @NotNull
  @Override
  public List<Integer> getUpNodes(int nodeIndex) {
    Pair<Integer, Integer> edge = downToEdge.get(nodeIndex);
    if (edge != null)
      return Collections.singletonList(edge.first);

    List<Integer> upNodes = new SmartList<Integer>();
    for (int upNode : myPermanentGraph.getUpNodes(nodeIndex)) {
      if (nodeIsVisible(upNode))
        upNodes.add(upNode);
    }
    return upNodes;
  }

  @NotNull
  @Override
  public List<Integer> getDownNodes(int nodeIndex) {
    Pair<Integer, Integer> edge = upToEdge.get(nodeIndex);
    if (edge != null)
      return Collections.singletonList(edge.second);

    List<Integer> downNodes = new SmartList<Integer>();
    for (int downNode : myPermanentGraph.getDownNodes(nodeIndex)) {
      if (nodeIsVisible(downNode))
        downNodes.add(downNode);
    }
    return downNodes;

  }
}
