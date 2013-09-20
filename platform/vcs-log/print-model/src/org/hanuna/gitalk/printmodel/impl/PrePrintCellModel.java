package org.hanuna.gitalk.printmodel.impl;

import org.hanuna.gitalk.graph.elements.Edge;
import org.hanuna.gitalk.graph.elements.GraphElement;
import org.hanuna.gitalk.graph.elements.Node;
import org.hanuna.gitalk.printmodel.CommitSelectController;
import org.hanuna.gitalk.printmodel.SelectController;
import org.hanuna.gitalk.printmodel.ShortEdge;
import org.hanuna.gitalk.printmodel.SpecialPrintElement;
import org.hanuna.gitalk.printmodel.layout.LayoutModel;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
class PrePrintCellModel {
  private final GraphElementsVisibilityController visibilityController;
  private List<GraphElement> visibleElementsInThisRow;
  private final int rowIndex;
  private final SelectController selectController;
  private final CommitSelectController commitSelectController;

  public PrePrintCellModel(boolean hideLongEdge,
                           @NotNull LayoutModel layoutModel,
                           int rowIndex,
                           @NotNull SelectController selectController, CommitSelectController commitSelectController) {
    this.commitSelectController = commitSelectController;
    visibilityController = new GraphElementsVisibilityController(hideLongEdge, layoutModel);
    this.rowIndex = rowIndex;
    this.selectController = selectController;
    visibleElementsInThisRow = visibilityController.visibleElements(rowIndex);
  }

  public PrePrintCellModel(@NotNull LayoutModel layoutModel,
                           int rowIndex,
                           @NotNull SelectController selectController,
                           CommitSelectController commitSelectController) {
    this(true, layoutModel, rowIndex, selectController, commitSelectController);
  }

  public int getCountCells() {
    return visibleElementsInThisRow.size();
  }

  private boolean isMarked(Edge edge) {
    return commitSelectController.isSelected(edge.getUpNode()) && commitSelectController.isSelected(edge.getDownNode());
  }

  @NotNull
  public List<SpecialPrintElement> getSpecialPrintElements() {
    List<SpecialPrintElement> specialPrintElements = new ArrayList<SpecialPrintElement>();

    for (int i = 0; i < visibleElementsInThisRow.size(); i++) {
      GraphElement element = visibleElementsInThisRow.get(i);
      Node node = element.getNode();
      if (node != null) {
        if (node.getType() == Node.NodeType.COMMIT_NODE) {
          int dragAndDropSelect = 0;
          if (node == commitSelectController.getDragAndDropNode()) {
            if (commitSelectController.isAbove()) {
              dragAndDropSelect = 1;
            } else {
              dragAndDropSelect = -1;
            }
          }
          specialPrintElements
            .add(new SpecialPrintElement(node, i, SpecialPrintElement.Type.COMMIT_NODE, selectController.isSelected(node),
                                         commitSelectController.isSelected(node), dragAndDropSelect));
        }
      }
      else {
        Edge edge = element.getEdge();
        if (edge == null) {
          throw new IllegalStateException();
        }
        switch (visibilityController.visibilityTypeEdge(edge, rowIndex)) {
          case HIDE:
            // do nothing
            break;
          case USUAL:
            // do nothing
            break;
          case LAST_VISIBLE:
            specialPrintElements
              .add(new SpecialPrintElement(edge, i, SpecialPrintElement.Type.DOWN_ARROW, selectController.isSelected(edge), isMarked(edge),
                                           0));
            break;
          case FIRST_VISIBLE:
            specialPrintElements
              .add(new SpecialPrintElement(edge, i, SpecialPrintElement.Type.UP_ARROW, selectController.isSelected(edge), isMarked(edge),
                                           0));
            break;
          default:
            throw new IllegalStateException();
        }
      }
    }
    return Collections.unmodifiableList(specialPrintElements);
  }

  @NotNull
  public List<ShortEdge> downShortEdges() {
    GetterGraphElementPosition getter = new GetterGraphElementPosition(visibilityController.visibleElements(rowIndex + 1));

    List<ShortEdge> shortEdges = new ArrayList<ShortEdge>();
    // start with add shortEdges from Node
    for (int p = 0; p < visibleElementsInThisRow.size(); p++) {
      Node node = visibleElementsInThisRow.get(p).getNode();
      if (node != null) {
        for (Edge edge : node.getDownEdges()) {
          int to = getter.getPosition(edge);
          assert to != -1;
          shortEdges.add(new ShortEdge(edge, p, to, selectController.isSelected(edge), isMarked(edge)));
        }
      }
    }
    for (int p = 0; p < visibleElementsInThisRow.size(); p++) {
      Edge edge = visibleElementsInThisRow.get(p).getEdge();
      if (edge != null) {
        int to = getter.getPosition(edge);
        if (to >= 0) {
          shortEdges.add(new ShortEdge(edge, p, to, selectController.isSelected(edge), isMarked(edge)));
        }
      }
    }

    return Collections.unmodifiableList(shortEdges);
  }

  private static class GetterGraphElementPosition {
    private final Map<Node, Integer> mapNodes = new HashMap<Node, Integer>();

    public GetterGraphElementPosition(List<GraphElement> graphElements) {
      mapNodes.clear();
      for (int p = 0; p < graphElements.size(); p++) {
        mapNodes.put(getDownNode(graphElements.get(p)), p);
      }
    }

    private Node getDownNode(@NotNull GraphElement element) {
      Node node = element.getNode();
      if (node != null) {
        return node;
      }
      else {
        Edge edge = element.getEdge();
        if (edge == null) {
          throw new IllegalStateException();
        }
        return edge.getDownNode();
      }
    }

    public int getPosition(Edge edge) {
      Integer p = mapNodes.get(edge.getDownNode());
      if (p == null) {
        // i.e. hide branch
        return -1;
      }
      return p;
    }

  }

}
