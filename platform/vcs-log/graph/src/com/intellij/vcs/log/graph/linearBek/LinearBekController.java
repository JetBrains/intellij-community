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
package com.intellij.vcs.log.graph.linearBek;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.elements.GraphNode;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.impl.facade.BekBaseLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.CascadeLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.GraphChangesUtil;
import com.intellij.vcs.log.graph.impl.facade.bek.BekIntMap;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class LinearBekController extends CascadeLinearGraphController {
  private static final Logger LOG = Logger.getInstance(LinearBekController.class);
  @NotNull private final LinearBekGraph myCompiledGraph;
  private final LinearBekGraphBuilder myLinearBekGraphBuilder;
  private final BekGraphLayout myBekGraphLayout;

  public LinearBekController(@NotNull BekBaseLinearGraphController controller, @NotNull PermanentGraphInfo permanentGraphInfo) {
    super(controller, permanentGraphInfo);
    myCompiledGraph = new LinearBekGraph(getDelegateGraph());
    myBekGraphLayout = new BekGraphLayout(permanentGraphInfo.getPermanentGraphLayout(), controller.getBekIntMap());
    myLinearBekGraphBuilder = new LinearBekGraphBuilder(myCompiledGraph, myBekGraphLayout);

    long start = System.currentTimeMillis();
    myLinearBekGraphBuilder.collapseAll();
    LOG.info("Linear bek took " + (System.currentTimeMillis() - start) / 1000.0 + " sec");
  }

  @NotNull
  @Override
  protected LinearGraphAnswer delegateGraphChanged(@NotNull LinearGraphAnswer delegateAnswer) {
    return delegateAnswer;
  }

  @Nullable
  @Override
  protected LinearGraphAnswer performAction(@NotNull LinearGraphAction action) {
    if (action.getAffectedElement() != null) {
      if (action.getType() == GraphAction.Type.MOUSE_CLICK) {
        GraphElement graphElement = action.getAffectedElement().getGraphElement();
        if (graphElement instanceof GraphEdge) {
          return expandEdge((GraphEdge)graphElement);
        }
        else if (graphElement instanceof GraphNode) {
          return collapseNode((GraphNode)graphElement);
        }
      }
      else if (action.getType() == GraphAction.Type.MOUSE_OVER) {
        GraphElement graphElement = action.getAffectedElement().getGraphElement();
        if (graphElement instanceof GraphEdge) {
          return highlightEdge((GraphEdge)graphElement);
        }
        else if (graphElement instanceof GraphNode) {
          return highlightNode((GraphNode)graphElement);
        }
      }
    }
    else if (action.getType() == GraphAction.Type.BUTTON_COLLAPSE) {
      return collapseAll();
    }
    else if (action.getType() == GraphAction.Type.BUTTON_EXPAND) {
      return expandAll();
    }
    return null;
  }

  @NotNull
  private LinearGraphAnswer expandAll() {
    return new LinearGraphAnswer(GraphChangesUtil.SOME_CHANGES, null, null, null) {
      @Nullable
      @Override
      public Runnable getGraphUpdater() {
        return new Runnable() {
          @Override
          public void run() {
            myCompiledGraph.myDottedEdges.removeAll();
            myCompiledGraph.myHiddenEdges.removeAll();
          }
        };
      }
    };
  }

  @NotNull
  private LinearGraphAnswer collapseAll() {
    final LinearBekGraph.WorkingLinearBekGraph workingGraph = new LinearBekGraph.WorkingLinearBekGraph(myCompiledGraph);
    new LinearBekGraphBuilder(workingGraph, myBekGraphLayout).collapseAll();
    return new LinearGraphAnswer(
      GraphChangesUtil.edgesReplaced(workingGraph.getRemovedEdges(), workingGraph.getAddedEdges(), getDelegateGraph()), null, null, null) {
      @Nullable
      @Override
      public Runnable getGraphUpdater() {
        return new Runnable() {
          @Override
          public void run() {
            workingGraph.applyChanges();
          }
        };
      }
    };
  }

  @Nullable
  private LinearGraphAnswer highlightNode(GraphNode node) {
    SortedSet<Integer> toCollapse = collectNodesToCollapse(node);

    if (toCollapse.isEmpty()) return null;

    Set<Integer> toHighlight = ContainerUtil.newHashSet();

    LinearBekGraphBuilder builder = new LinearBekGraphBuilder(new LinearBekGraph(myCompiledGraph), myBekGraphLayout);
    for (Integer i : toCollapse) {
      LinearBekGraphBuilder.MergeFragment fragment = builder.collapseFragment(i);
      if (fragment != null) {
        toHighlight.addAll(fragment.getAllNodes());
      }
    }

    return LinearGraphUtils.createSelectedAnswer(myCompiledGraph, toHighlight);
  }

  @Nullable
  private LinearGraphAnswer highlightEdge(GraphEdge edge) {
    if (edge.getType() == GraphEdgeType.DOTTED) {
      return LinearGraphUtils.createSelectedAnswer(myCompiledGraph, ContainerUtil.set(edge.getUpNodeIndex(), edge.getDownNodeIndex()));
    }
    return null;
  }

  @Nullable
  private LinearGraphAnswer collapseNode(GraphNode node) {
    SortedSet<Integer> toCollapse = collectNodesToCollapse(node);

    if (toCollapse.isEmpty()) return null;

    for (Integer i : toCollapse) {
      myLinearBekGraphBuilder.collapseFragment(i);
    }
    return new LinearGraphAnswer(GraphChangesUtil.SOME_CHANGES, null, null, null);
  }

  @NotNull
  private SortedSet<Integer> collectNodesToCollapse(GraphNode node) {
    SortedSet<Integer> toCollapse = new TreeSet<Integer>(new Comparator<Integer>() {
      @Override
      public int compare(Integer o1, Integer o2) {
        return o2.compareTo(o1);
      }
    });

    int mergesCount = 0;

    LinkedHashSet<Integer> toProcess = ContainerUtil.newLinkedHashSet();
    toProcess.add(node.getNodeIndex());
    while (!toProcess.isEmpty()) {
      Integer i = ContainerUtil.getFirstItem(toProcess);
      toProcess.remove(i);

      LinearBekGraphBuilder.MergeFragment fragment = myLinearBekGraphBuilder.getFragment(i);
      if (fragment == null) continue;

      toCollapse.add(i);
      toCollapse.addAll(fragment.getTailsAndBody());
      toProcess.addAll(fragment.getTailsAndBody());

      mergesCount++;
      if (mergesCount > 10) break;
    }
    return toCollapse;
  }

  @Nullable
  private LinearGraphAnswer expandEdge(GraphEdge edge) {
    if (edge.getType() == GraphEdgeType.DOTTED) {
      return new LinearGraphAnswer(
        GraphChangesUtil.edgesReplaced(Collections.singleton(edge), myCompiledGraph.expandEdge(edge), getDelegateGraph()), null, null,
        null);
    }
    return null;
  }

  @NotNull
  private LinearGraph getDelegateGraph() {
    return getDelegateLinearGraphController().getCompiledGraph();
  }

  @NotNull
  @Override
  public LinearGraph getCompiledGraph() {
    return myCompiledGraph;
  }

  private static class BekGraphLayout implements GraphLayout {
    private final GraphLayout myGraphLayout;
    private final BekIntMap myBekIntMap;

    public BekGraphLayout(GraphLayout graphLayout, BekIntMap bekIntMap) {
      myGraphLayout = graphLayout;
      myBekIntMap = bekIntMap;
    }

    @Override
    public int getLayoutIndex(int nodeIndex) {
      return myGraphLayout.getLayoutIndex(myBekIntMap.getUsualIndex(nodeIndex));
    }

    @Override
    public int getOneOfHeadNodeIndex(int nodeIndex) {
      int usualIndex = myGraphLayout.getOneOfHeadNodeIndex(myBekIntMap.getUsualIndex(nodeIndex));
      return myBekIntMap.getBekIndex(usualIndex);
    }

    @NotNull
    @Override
    public List<Integer> getHeadNodeIndex() {
      List<Integer> bekIndexes = new ArrayList<Integer>();
      for (int head : myGraphLayout.getHeadNodeIndex()) {
        bekIndexes.add(myBekIntMap.getBekIndex(head));
      }
      return bekIndexes;
    }
  }

}
