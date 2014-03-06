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
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.compressedlist.UpdateRequest;
import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.GraphElement;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.elements.NodeRow;
import com.intellij.vcs.log.graph.render.SimpleGraphCellPainter;
import com.intellij.vcs.log.graphmodel.FragmentManager;
import com.intellij.vcs.log.graphmodel.GraphFragment;
import com.intellij.vcs.log.graphmodel.GraphModel;
import com.intellij.vcs.log.printmodel.GraphPrintCell;
import com.intellij.vcs.log.printmodel.GraphPrintCellModel;
import com.intellij.vcs.log.printmodel.SelectController;
import com.intellij.vcs.log.printmodel.SpecialPrintElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.vcs.log.graph.render.PrintParameters.HEIGHT_CELL;
import static com.intellij.vcs.log.graph.render.PrintParameters.WIDTH_NODE;

public class GraphFacadeImpl implements GraphFacade {

  private static final Logger LOG = Logger.getInstance(GraphFacadeImpl.class);
  private static final Function<Node,Boolean> ALL_NODES_VISIBLE = new Function<Node, Boolean>() {
    @Override
    public Boolean fun(Node node) {
      return true;
    }
  };

  // In case of diagonal edges, one node can be at most 3 "arrows" + 2 nodes at the left from another - that is enough for sure
  private static final int IMAGE_WIDTH_RESERVE = 5 * WIDTH_NODE;

  @NotNull private final GraphModel myGraphModel;
  @NotNull private final GraphPrintCellModel myPrintCellModel;
  @NotNull private final GraphColorManager myColorManager;
  @NotNull private final SimpleGraphCellPainter myGraphPainter;

  @Nullable private GraphElement prevGraphElement;

  public GraphFacadeImpl(@NotNull GraphModel graphModel, @NotNull GraphPrintCellModel printCellModel,
                         @NotNull GraphColorManager colorManager) {
    myGraphModel = graphModel;
    myPrintCellModel = printCellModel;
    myColorManager = colorManager;
    myGraphPainter = new SimpleGraphCellPainter();
  }

  @NotNull
  @Override
  public PaintInfo paint(int visibleRow) {
    GraphPrintCell cell = myPrintCellModel.getGraphPrintCell(visibleRow);
    int imageWidth = calcImageWidth(cell);
    int bufferWidth = imageWidth + IMAGE_WIDTH_RESERVE;
    BufferedImage image = UIUtil.createImage(bufferWidth, HEIGHT_CELL, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = image.createGraphics();
    myGraphPainter.draw(g2, cell);
    return new PaintInfoImpl(image, imageWidth);
  }

  private static int calcImageWidth(@NotNull GraphPrintCell cell) {
    return cell.countCell() * WIDTH_NODE;
  }

  @Nullable
  @Override
  public GraphAnswer performAction(@NotNull GraphAction action) {
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
      return handleClick((ClickGraphAction)action);
    }
    else if (action instanceof MouseOverAction) {
      return handleMouseOver((MouseOverAction)action);
    }
    return null;
  }

  @Nullable
  private GraphAnswer handleMouseOver(MouseOverAction action) {
    GraphPrintCell printCell = myPrintCellModel.getGraphPrintCell(action.getRow());
    Node jumpToNode = arrowToNode(action.getRelativePoint(), printCell);
    if (jumpToNode != null) {
      over(null);
      return new ChangeCursorAnswer(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    else {
      over(overCell(action.getRelativePoint(), printCell));
      return new ChangeCursorAnswer(Cursor.getDefaultCursor());
    }
  }

  @Nullable
  private GraphAnswer handleClick(@NotNull ClickGraphAction action) {
    if (action.getRelativePoint() == null) {
      return handleRowSelection(action.getRow());
    }
    else {
      GraphPrintCell printCell = myPrintCellModel.getGraphPrintCell(action.getRow());
      return handleMouseClick(action.getRow(), action.getRelativePoint(), printCell);
    }
  }

  @Nullable
  private GraphAnswer handleRowSelection(int row) {
    myPrintCellModel.getCommitSelectController().deselectAll();
    Node node = myGraphModel.getGraph().getCommitNodeInRow(row);
    if (node != null) {
      FragmentManager fragmentController = myGraphModel.getFragmentManager();
      myPrintCellModel.getCommitSelectController().select(fragmentController.allCommitsCurrentBranch(node));
    }
    return null;
  }

  @Nullable
  private GraphAnswer handleMouseClick(int row, @NotNull Point point, @Nullable GraphPrintCell printCell) {
    Node jumpToNode = arrowToNode(point, printCell);
    if (jumpToNode != null) {
      return new JumpToRowAnswer(jumpToNode.getRowIndex());
    }
    GraphElement graphElement = overCell(point, printCell);
    myPrintCellModel.getSelectController().deselectAll();
    if (graphElement == null) {
      return handleRowSelection(row);
    }
    else {
      return click(graphElement);
    }
  }

  @Nullable
  private Node arrowToNode(@NotNull Point point, @Nullable GraphPrintCell row) {
    if (row == null) {
      return null;
    }
    SpecialPrintElement printElement = myGraphPainter.mouseOverArrow(row, point.x, point.y);
    if (printElement == null) {
      return null;
    }
    Edge edge = printElement.getGraphElement().getEdge();
    if (edge == null) {
      return null;
    }
    return printElement.getType() == SpecialPrintElement.Type.DOWN_ARROW ? edge.getDownNode() : edge.getUpNode();
  }

  @Nullable
  public GraphAnswer click(@Nullable GraphElement graphElement) {
    FragmentManager fragmentController = myGraphModel.getFragmentManager();
    if (graphElement == null) {
      return null;
    }
    final GraphFragment fragment = fragmentController.relateFragment(graphElement);
    if (fragment == null) {
      return null;
    }

    UpdateRequest updateRequest = fragmentController.changeVisibility(fragment);
    return new JumpToRowAnswer(updateRequest.from());
  }

  public void over(@Nullable GraphElement graphElement) {
    SelectController selectController = myPrintCellModel.getSelectController();
    FragmentManager fragmentManager = myGraphModel.getFragmentManager();
    if (graphElement == prevGraphElement) {
      return;
    }
    else {
      prevGraphElement = graphElement;
    }
    selectController.deselectAll();
    if (graphElement != null) {
      GraphFragment graphFragment = fragmentManager.relateFragment(graphElement);
      selectController.select(graphFragment);
    }
  }

  @NotNull
  @Override
  public List<Integer> getAllCommits() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  private GraphElement overCell(@NotNull Point point, @Nullable GraphPrintCell row) {
    int y = point.y;
    int x = point.x;
    return row != null ? myGraphPainter.mouseOver(row, x, y) : null;
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

    @NotNull
    @Override
    public Set<Integer> getContainingBranches(int visibleRow) {
      throw new UnsupportedOperationException();
    }

    @NotNull
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

  private static class JumpToRowAnswer implements GraphAnswer {
    private final int myRow;

    public JumpToRowAnswer(int row) {
      myRow = row;
    }

    @Nullable
    @Override
    public GraphChange getGraphChange() {
      return null;
    }

    @Nullable
    @Override
    public GraphActionRequest getActionRequest() {
      return new JumpToRowActionRequest(myRow);
    }

  }

  private static class ChangeCursorAnswer implements GraphAnswer {
    private final Cursor myCursor;

    public ChangeCursorAnswer(@NotNull Cursor cursor) {
      myCursor = cursor;
    }

    @Nullable
    @Override
    public GraphChange getGraphChange() {
      return null;
    }

    @Nullable
    @Override
    public GraphActionRequest getActionRequest() {
      return new ChangeCursorActionRequest(myCursor);
    }
  }

  private static class PaintInfoImpl implements PaintInfo {

    private final Image myImage;
    private final int myWidth;

    public PaintInfoImpl(Image image, int width) {
      myImage = image;
      myWidth = width;
    }

    @NotNull
    @Override
    public Image getImage() {
      return myImage;
    }

    @Override
    public int getWidth() {
      return myWidth;
    }
  }
}
