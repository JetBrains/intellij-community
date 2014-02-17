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
package com.intellij.vcs.log.graph;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.elements.NodeRow;
import com.intellij.vcs.log.graphmodel.FragmentManager;
import com.intellij.vcs.log.graphmodel.GraphModel;
import com.intellij.vcs.log.printmodel.GraphPrintCellModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class GraphFacadeImpl implements GraphBlackBox {

  private static final Logger LOG = Logger.getInstance(GraphFacadeImpl.class);
  private static final Function<Node,Boolean> ALL_NODES_VISIBLE = new Function<Node, Boolean>() {
    @Override
    public Boolean fun(Node node) {
      return true;
    }
  };

  private final GraphModel myGraphModel;
  private final GraphPrintCellModel myPrintCellModel;

  public GraphFacadeImpl(GraphModel graphModel, GraphPrintCellModel printCellModel) {
    myGraphModel = graphModel;
    myPrintCellModel = printCellModel;
  }

  @Override
  public void paint(Graphics2D g, int visibleRow) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public GraphChangeEvent performAction(@NotNull GraphAction action) {
    if (action instanceof LinearBranchesExpansionAction) {
      FragmentManager fragmentManager = myGraphModel.getFragmentManager();
      if (((LinearBranchesExpansionAction)action).shouldExpand()) {
        fragmentManager.showAll();
      }
      else {
        fragmentManager.hideAll();
      }
    }
    else if (action instanceof LongEdgesAction) {
      myPrintCellModel.setLongEdgeVisibility(((LongEdgesAction)action).shouldShowLongEdges());
    }
    else if (action instanceof ClickGraphAction) {
      myPrintCellModel.getCommitSelectController().deselectAll();
      Node node = myGraphModel.getGraph().getCommitNodeInRow(((ClickGraphAction)action).getRow());
      if (node != null) {
        FragmentManager fragmentController = myGraphModel.getFragmentManager();
        myPrintCellModel.getCommitSelectController().select(fragmentController.allCommitsCurrentBranch(node));
      }
    }
    return null;
  }

  @NotNull
  @Override
  public List<Integer> getAllCommits() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public List<Integer> getVisibleCommits() {
    List<NodeRow> nodeRows = myGraphModel.getGraph().getNodeRows();
    return ContainerUtil.map(nodeRows, new Function<NodeRow, Integer>() {
      @Override
      public Integer fun(NodeRow nodeRow) {
        return getCommit(nodeRow);
      }
    });
  }

  @Override
  public int getCommitAtRow(int visibleRow) {
    NodeRow row = myGraphModel.getGraph().getNodeRows().get(visibleRow);
    return getCommit(row);
  }

  private static int getCommit(@NotNull NodeRow nodeRow) {
    List<Node> nodes = nodeRow.getNodes();
    if (nodes.size() < 1) {
      LOG.error("No nodes for nodeRow: " + nodeRow);
      return -1;
    }
    else {
      if (nodes.size() > 1 && existsNotEndNode(nodes)) { // allowed for END_NODES, i.e. at the bottom of the partly loaded log
        LOG.error("Too many nodes for nodeRow: " + nodeRow);
      }
      return nodes.get(0).getCommitIndex();
    }
  }

  @Override
  public int getVisibleCommitCount() {
    return myGraphModel.getGraph().getNodeRows().size();
  }

  private static boolean existsNotEndNode(@NotNull List<Node> nodes) {
    return ContainerUtil.exists(nodes, new Condition<Node>() {
      @Override
      public boolean value(Node node) {
        return node.getType() != Node.NodeType.END_COMMIT_NODE;
      }
    });
  }

  @Override
  public void setVisibleBranches(@Nullable final Collection<Integer> heads) {
    myGraphModel.setVisibleBranchesNodes(heads == null ? ALL_NODES_VISIBLE : new Function<Node, Boolean>() {
      @Override
      public Boolean fun(final Node node) {
        return heads.contains(node.getCommitIndex());
      }
    });
  }

  @Override
  public void setFilter(@NotNull Condition<Integer> visibilityPredicate) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GraphInfoProvider getInfoProvider() {
    return new GraphInfoProviderImpl();
  }

  private class GraphInfoProviderImpl implements GraphInfoProvider {

    @Override
    public int getOneOfHeads(int commit) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<Integer> getContainingBranches(int commitIndex) {
      throw new UnsupportedOperationException();
    }

    @Override
    public RowInfo getRowInfo(int visibleRow) {
      return new RowInfoImpl(visibleRow);
    }

    @Override
    public boolean areLongEdgesHidden() {
      return myPrintCellModel.areLongEdgesHidden();
    }
  }

  private class RowInfoImpl implements GraphInfoProvider.RowInfo {
    private final int myVisibleRow;

    public RowInfoImpl(int visibleRow) {
      myVisibleRow = visibleRow;
    }

    @Override
    public int getOneOfHeads() {
      Node node = myGraphModel.getGraph().getCommitNodeInRow(myVisibleRow);
      assert node != null : "node is null for row " + myVisibleRow;
      return node.getBranch().getOneOfHeads();
    }
  }

}
