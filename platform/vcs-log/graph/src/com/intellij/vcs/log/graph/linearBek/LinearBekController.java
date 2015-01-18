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

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.actions.GraphAction;
import com.intellij.vcs.log.graph.api.GraphLayout;
import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.elements.GraphEdge;
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType;
import com.intellij.vcs.log.graph.api.elements.GraphElement;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.api.printer.PrintElementWithGraphElement;
import com.intellij.vcs.log.graph.impl.facade.BekBaseLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.CascadeLinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.GraphChanges;
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController;
import com.intellij.vcs.log.graph.impl.facade.bek.BekIntMap;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

public class LinearBekController extends CascadeLinearGraphController {
  @NotNull private final LinearBekGraph myCompiledGraph;

  public LinearBekController(@NotNull BekBaseLinearGraphController controller,
                             @NotNull PermanentGraphInfo permanentGraphInfo,
                             @NotNull final TimestampGetter timestampGetter) {
    super(controller, permanentGraphInfo);
    final BekIntMap bekIntMap = controller.getBekIntMap();
    myCompiledGraph = compileGraph(getDelegateLinearGraphController().getCompiledGraph(),
                                   new BekGraphLayout(permanentGraphInfo.getPermanentGraphLayout(), bekIntMap),
                                   new BekTimestampGetter(timestampGetter, bekIntMap));
  }

  static LinearBekGraph compileGraph(@NotNull LinearGraph graph,
                                     @NotNull GraphLayout graphLayout,
                                     @NotNull TimestampGetter timestampGetter) {
    long start = System.currentTimeMillis();
    LinearBekGraph result = new LinearBekGraphBuilder(graph, graphLayout, timestampGetter).build();
    long end = System.currentTimeMillis();
    System.err.println(((double)end - start) / 1000);
    return result;
  }

  @Override
  protected boolean elementIsSelected(@NotNull PrintElementWithGraphElement printElement) {
    return false;
  }

  @NotNull
  @Override
  protected LinearGraphAnswer performDelegateUpdate(@NotNull LinearGraphAnswer delegateAnswer) {
    return delegateAnswer;
  }

  @Nullable
  @Override
  protected LinearGraphAnswer performAction(@NotNull LinearGraphAction action) {
    if (action.getAffectedElement() != null) {
      if (action.getType() == GraphAction.Type.MOUSE_CLICK) {
        GraphElement graphElement = action.getAffectedElement().getGraphElement();
        if (graphElement instanceof GraphEdge) {
          GraphEdge edge = (GraphEdge)graphElement;
          if (edge.getType() == GraphEdgeType.DOTTED) {
            return new ExpandedEdgeAnswer(edge, myCompiledGraph.expandEdge(edge));
          }
        }
      }
      else if (action.getType() == GraphAction.Type.MOUSE_OVER) {
        GraphElement graphElement = action.getAffectedElement().getGraphElement();
        if (graphElement instanceof GraphEdge) {
          GraphEdge edge = (GraphEdge)graphElement;
          if (edge.getType() == GraphEdgeType.DOTTED){
            return new CursorAnswer(Cursor.HAND_CURSOR);
          }
        }
      }
    }
    return new CursorAnswer(Cursor.DEFAULT_CURSOR);
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

  private static class BekTimestampGetter implements TimestampGetter {
    private final TimestampGetter myTimestampGetter;
    private final BekIntMap myBekIntMap;

    public BekTimestampGetter(TimestampGetter timestampGetter, BekIntMap bekIntMap) {
      myTimestampGetter = timestampGetter;
      myBekIntMap = bekIntMap;
    }

    @Override
    public int size() {
      return myTimestampGetter.size();
    }

    @Override
    public long getTimestamp(int index) {
      return myTimestampGetter.getTimestamp(myBekIntMap.getUsualIndex(index));
    }

  }

  private static class ExpandedEdgeAnswer implements LinearGraphAnswer {
    private final GraphChanges<Integer> myChanges;

    private ExpandedEdgeAnswer(@NotNull GraphEdge expanded, @NotNull Collection<GraphEdge> addedEdges) {
      final Set<GraphChanges.Edge<Integer>> edgeChanges = ContainerUtil.newHashSet();

      edgeChanges.add(new ChangedEdge(expanded.getUpNodeIndex(), expanded.getDownNodeIndex(), true));
      for (GraphEdge edge : addedEdges) {
        edgeChanges.add(new ChangedEdge(edge.getUpNodeIndex(), edge.getDownNodeIndex(), false));
      }

      myChanges = new GraphChanges<Integer>() {
        @NotNull
        @Override
        public Collection<Node<Integer>> getChangedNodes() {
          return Collections.emptySet();
        }

        @NotNull
        @Override
        public Collection<Edge<Integer>> getChangedEdges() {
          return edgeChanges;
        }
      };
    }

    @Nullable
    @Override
    public GraphChanges<Integer> getGraphChanges() {
      return myChanges;
    }

    @Nullable
    @Override
    public Cursor getCursorToSet() {
      return null; // TODO
    }

    @Nullable
    @Override
    public Integer getCommitToJump() {
      return null; // TODO
    }

    private static class ChangedEdge implements GraphChanges.Edge<Integer> {
      private final int myUpNodeId;
      private final int myDownNodeId;
      private final boolean myRemoved;

      private ChangedEdge(int upNodeId, int downNodeId, boolean removed) {
        myUpNodeId = upNodeId;
        myDownNodeId = downNodeId;
        myRemoved = removed;
      }

      @Nullable
      @Override
      public Integer upNodeId() {
        return myUpNodeId;
      }

      @Nullable
      @Override
      public Integer downNodeId() {
        return myDownNodeId;
      }

      @Nullable
      @Override
      public Integer additionInfo() {
        return null; // TODO huh?
      }

      @Override
      public boolean removed() {
        return myRemoved;
      }
    }
  }

  private static class CursorAnswer implements LinearGraphController.LinearGraphAnswer {
    private final int myCursorType;

    public CursorAnswer(int cursorType) {
      myCursorType = cursorType;
    }

    @Nullable
    @Override
    public GraphChanges<Integer> getGraphChanges() {
      return null;
    }

    @Nullable
    @Override
    public Cursor getCursorToSet() {
      return Cursor.getPredefinedCursor(myCursorType);
    }

    @Nullable
    @Override
    public Integer getCommitToJump() {
      return null;
    }
  }
}
