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

import com.intellij.vcs.log.facade.utils.impl.IDIntToIntMap;
import com.intellij.vcs.log.newgraph.PermanentGraph;
import com.intellij.vcs.log.newgraph.PermanentGraphLayout;
import com.intellij.vcs.log.newgraph.gpaph.Edge;
import com.intellij.vcs.log.newgraph.gpaph.GraphWithElementsInfo;
import com.intellij.vcs.log.newgraph.gpaph.Node;
import com.intellij.vcs.log.newgraph.gpaph.ThickHoverController;
import com.intellij.vcs.log.newgraph.gpaph.actions.InternalGraphAction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PermanentAsMutableGraph extends MutableGraphWithHiddenNodes<PermanentAsMutableGraph.GraphWithElementsInfoImpl> {

  @NotNull
  private final ThickHoverController myThickHoverController;

  public PermanentAsMutableGraph(@NotNull PermanentGraph graph,
                                 @NotNull PermanentGraphLayout layout,
                                 @NotNull ThickHoverController thickHoverController) {
    super(new IDIntToIntMap(graph.nodesCount()), new GraphWithElementsInfoImpl(graph), layout);
    myThickHoverController = thickHoverController;
  }

  @Override
  public int performAction(@NotNull InternalGraphAction action) {
    return -1;
  }

  @NotNull
  @Override
  public ThickHoverController getThickHoverController() {
    return myThickHoverController;
  }

  protected static class GraphWithElementsInfoImpl implements GraphWithElementsInfo {
    private final PermanentGraph myGraph;

    private GraphWithElementsInfoImpl(PermanentGraph graph) {
      myGraph = graph;
    }

    @NotNull
    @Override
    public Node.Type getNodeType(int nodeIndex) {
      return Node.Type.USUAL;
    }

    @NotNull
    @Override
    public Edge.Type getEdgeType(int upNodeIndex, int downNodeIndex) {
      return Edge.Type.USUAL;
    }

    @Override
    public int nodesCount() {
      return myGraph.nodesCount();
    }

    @NotNull
    @Override
    public List<Integer> getUpNodes(int nodeIndex) {
      return myGraph.getUpNodes(nodeIndex);
    }

    @NotNull
    @Override
    public List<Integer> getDownNodes(int nodeIndex) {
      return myGraph.getDownNodes(nodeIndex);
    }
  }
}
