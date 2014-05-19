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

import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.LinearGraphWithHiddenNodes;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.utils.DfsUtil;
import com.intellij.vcs.log.graph.utils.Flags;
import com.intellij.vcs.log.graph.utils.ListenerController;
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags;
import com.intellij.vcs.log.graph.utils.impl.SetListenerController;
import gnu.trove.TIntIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class CollapsedGraphWithHiddenNodes implements LinearGraphWithHiddenNodes {
  @NotNull
  private final LinearGraphWithHiddenNodes myDelegateGraph;

  @NotNull
  private final Flags myVisibleNodes;

  @NotNull
  private final DfsUtil myDfsUtil = new DfsUtil();

  @NotNull
  private final TIntIntHashMap upToEdge = new TIntIntHashMap();

  @NotNull
  private final TIntIntHashMap downToEdge = new TIntIntHashMap();

  @NotNull
  private final SetListenerController<UpdateListener> myListenerController = new SetListenerController<UpdateListener>();

  public CollapsedGraphWithHiddenNodes(@NotNull LinearGraphWithHiddenNodes delegateGraph) {
    myDelegateGraph = delegateGraph;
    delegateGraph.getListenerController().addListener(new UpdateListener() {
      @Override
      public void update(int upNodeIndex, int downNodeIndex) {
        callListeners(upNodeIndex, downNodeIndex);
      }
    });
    myVisibleNodes = new BitSetFlags(delegateGraph.nodesCount(), true);
  }

  // nodes up and down must be loaded
  public void fastCollapse(int upNodeIndex, final int downNodeIndex) {
    myDfsUtil.nodeDfsIterator(upNodeIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        if (upToEdge.containsKey(currentNode)) {
          int downNode = upToEdge.remove(currentNode);
          downToEdge.remove(downNode);
          if (downNode == downNodeIndex)
            return NODE_NOT_FOUND;

          myVisibleNodes.set(downNode, false);
          return downNode;
        }

        for (int downNode : myDelegateGraph.getDownNodes(currentNode)) {
          if (myVisibleNodes.get(downNode) && downNode != downNodeIndex) {
            myVisibleNodes.set(downNode, false);
            return downNode;
          }
        }
        return NODE_NOT_FOUND;
      }
    });

    upToEdge.put(upNodeIndex, downNodeIndex);
    downToEdge.put(downNodeIndex, upNodeIndex);
  }

  public void collapse(int upNodeIndex, final int downNodeIndex) {
    fastCollapse(upNodeIndex, downNodeIndex);
    callListeners(upNodeIndex, downNodeIndex);
  }

  public void expand(int upNodeIndex, final int downNodeIndex) {
    upToEdge.remove(upNodeIndex);
    downToEdge.remove(downNodeIndex);

    myDfsUtil.nodeDfsIterator(upNodeIndex, new DfsUtil.NextNode() {
      @Override
      public int fun(int currentNode) {
        for (int downNode : myDelegateGraph.getDownNodes(currentNode)) {
          if (!myVisibleNodes.get(downNode) && downNode != downNodeIndex) {
            myVisibleNodes.set(downNode, true);
            return downNode;
          }
        }
        return NODE_NOT_FOUND;
      }
    });
    callListeners(upNodeIndex, downNodeIndex);
  }

  private void callListeners(final int upNodeIndex, final int downNodeIndex) {
    myListenerController.callListeners(new Consumer<UpdateListener>() {
      @Override
      public void consume(UpdateListener updateListener) {
        updateListener.update(upNodeIndex, downNodeIndex);
      }
    });
  }

  @Override
  public boolean nodeIsVisible(int nodeIndex) {
    if (nodeIndex == LinearGraph.NOT_LOAD_COMMIT)
      return true;
    return myVisibleNodes.get(nodeIndex) && myDelegateGraph.nodeIsVisible(nodeIndex);
  }

  public void callListeners() {
    callListeners(0, myDelegateGraph.nodesCount() - 1);
  }

  public void expandAll() {
    upToEdge.clear();
    downToEdge.clear();
    myVisibleNodes.setAll(true);
    callListeners();
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
    return myDelegateGraph.nodesCount();
  }

  @NotNull
  @Override
  public List<Integer> getUpNodes(int nodeIndex) {
    if (downToEdge.containsKey(nodeIndex))
      return Collections.singletonList(downToEdge.get(nodeIndex));

    List<Integer> upNodes = new SmartList<Integer>();
    for (int upNode : myDelegateGraph.getUpNodes(nodeIndex)) {
      if (nodeIsVisible(upNode))
        upNodes.add(upNode);
    }
    return upNodes;
  }

  @NotNull
  @Override
  public List<Integer> getDownNodes(int nodeIndex) {
    if (upToEdge.containsKey(nodeIndex))
      return Collections.singletonList(upToEdge.get(nodeIndex));

    List<Integer> downNodes = new SmartList<Integer>();
    for (int downNode : myDelegateGraph.getDownNodes(nodeIndex)) {
      if (nodeIsVisible(downNode))
        downNodes.add(downNode);
    }
    return downNodes;

  }
}
