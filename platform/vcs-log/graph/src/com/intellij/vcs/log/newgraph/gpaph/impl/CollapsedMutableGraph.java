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
package com.intellij.vcs.log.newgraph.gpaph.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.util.BooleanFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.vcs.log.newgraph.GraphFlags;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.PermanentGraphLayout;
import com.intellij.vcs.log.newgraph.SomeGraph;
import com.intellij.vcs.log.newgraph.gpaph.*;
import com.intellij.vcs.log.newgraph.gpaph.actions.ClickInternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.actions.InternalGraphAction;
import com.intellij.vcs.log.newgraph.gpaph.fragments.FragmentGenerator;
import com.intellij.vcs.log.newgraph.gpaph.fragments.GraphFragment;
import com.intellij.vcs.log.newgraph.utils.DfsUtil;
import com.intellij.vcs.log.newgraph.utils.Flags;
import com.intellij.vcs.log.newgraph.utils.MyUtils;
import com.intellij.vcs.log.newgraph.utils.impl.TreeIntToIntMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CollapsedMutableGraph extends AbstractMutableGraph<CollapsedMutableGraph.GraphWithElementsInfoImpl> {

  public static CollapsedMutableGraph newInstance(@NotNull PermanentGraph permanentGraph,
                                                  @NotNull PermanentGraphLayout layout,
                                                  @NotNull GraphFlags graphFlags,
                                                  @NotNull DfsUtil dfsUtil) {
    final Flags visibleNodes = graphFlags.getVisibleNodes();
    final Flags visibleNodesInBranches = graphFlags.getVisibleNodesInBranches();
    MyUtils.setAllValues(visibleNodes, true);
    TreeIntToIntMap intToIntMap = TreeIntToIntMap.newInstance(new BooleanFunction<Integer>() {
      @Override
      public boolean fun(Integer integer) {
        return visibleNodes.get(integer) && visibleNodesInBranches.get(integer);
      }
    }, permanentGraph.nodesCount());

    GraphWithElementsInfoImpl graphWithElementsInfo =
      new GraphWithElementsInfoImpl(permanentGraph, visibleNodesInBranches, visibleNodes, intToIntMap, dfsUtil);
    return new CollapsedMutableGraph(permanentGraph, layout, intToIntMap, graphFlags.getThickFlags(),
                                     dfsUtil, graphWithElementsInfo);
  }

  @NotNull
  private final FragmentGenerator myFragmentGenerator;

  @NotNull
  private final GraphWithElementsInfoImpl myGraphWithElementsInfo;

  @NotNull
  private final AbstractThickHoverController myThickHoverController;

  private CollapsedMutableGraph(@NotNull PermanentGraph permanentGraph,
                                @NotNull PermanentGraphLayout layout,
                                @NotNull TreeIntToIntMap intToIntMap,
                                @NotNull Flags thickFlags,
                                @NotNull DfsUtil dfsUtil,
                                @NotNull GraphWithElementsInfoImpl graphWithElementsInfo) {
    super(intToIntMap, graphWithElementsInfo, layout);
    myGraphWithElementsInfo = graphWithElementsInfo;
    myFragmentGenerator = new FragmentGenerator(this);
    myThickHoverController = new ThickHoverControllerImpl(permanentGraph, this, myFragmentGenerator, thickFlags, dfsUtil);
  }

  @Override
  public int performAction(@NotNull InternalGraphAction action) {
    myThickHoverController.performAction(action);
    if (action instanceof ClickInternalGraphAction) {
      GraphElement element = ((ClickInternalGraphAction)action).getInfo();
      if (element != null) {
        Edge edge = containedCollapsedEdge(element);
        if (edge != null) {
          myGraphWithElementsInfo.expand(getIndexInPermanentGraph(edge.getUpNodeVisibleIndex()),
                                         getIndexInPermanentGraph(edge.getDownNodeVisibleIndex()));
          return edge.getUpNodeVisibleIndex();
        }

        GraphFragment fragment = myFragmentGenerator.getLongFragment(element);
        if (fragment != null) {
          myGraphWithElementsInfo.collapse(getIndexInPermanentGraph(fragment.upVisibleNodeIndex),
                                           getIndexInPermanentGraph(fragment.downVisibleNodeIndex));
          return fragment.upVisibleNodeIndex;
        }
      }
    }
    return -1;
  }

  @NotNull
  @Override
  public ThickHoverController getThickHoverController() {
    return myThickHoverController;
  }

  protected static class GraphWithElementsInfoImpl implements GraphWithElementsInfo {
    @NotNull
    private final PermanentGraph myPermanentGraph;

    @NotNull
    private final Flags visibleNodesInBranches;

    @NotNull
    private final Flags visibleNodes;

    @NotNull
    private final TreeIntToIntMap intToIntMap;

    @NotNull
    private final DfsUtil myDfsUtil;

    @NotNull
    private final Map<Integer, Pair<Integer, Integer>> upToEdge = new HashMap<Integer, Pair<Integer, Integer>>();

    @NotNull
    private final Map<Integer, Pair<Integer, Integer>> downToEdge = new HashMap<Integer, Pair<Integer, Integer>>();

    public GraphWithElementsInfoImpl(@NotNull PermanentGraph permanentGraph,
                                     @NotNull Flags visibleNodesInBranches,
                                     @NotNull Flags visibleNodes,
                                     @NotNull TreeIntToIntMap intToIntMap, @NotNull DfsUtil dfsUtil) {
      myPermanentGraph = permanentGraph;
      this.visibleNodesInBranches = visibleNodesInBranches;
      this.visibleNodes = visibleNodes;
      this.intToIntMap = intToIntMap;
      myDfsUtil = dfsUtil;
    }

    public void collapse(int upNodeIndex, final int downNodeIndex) {
      Pair<Integer, Integer> edge = Pair.create(upNodeIndex, downNodeIndex);

      myDfsUtil.nodeDfsIterator(upNodeIndex, new DfsUtil.NextNode() {
        @Override
        public int fun(int currentNode) {
          if (upToEdge.containsKey(currentNode)) {
            Pair<Integer, Integer> edge = upToEdge.remove(currentNode);
            downToEdge.remove(edge.second);
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
      if (nodeIndex == SomeGraph.NOT_LOAD_COMMIT)
        return true;
      return visibleNodes.get(nodeIndex) && visibleNodesInBranches.get(nodeIndex);
    }

    @NotNull
    @Override
    public Node.Type getNodeType(int nodeIndex) {
      return Node.Type.USUAL;
    }

    @NotNull
    @Override
    public Edge.Type getEdgeType(int upNodeIndex, int downNodeIndex) {
      if (upToEdge.containsKey(upNodeIndex))
        return Edge.Type.HIDE_FRAGMENT;
      else
        return Edge.Type.USUAL;
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
}
