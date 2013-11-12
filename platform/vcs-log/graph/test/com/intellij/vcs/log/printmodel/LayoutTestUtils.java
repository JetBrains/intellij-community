package com.intellij.vcs.log.printmodel;

import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.GraphElement;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.printmodel.layout.LayoutModel;
import com.intellij.vcs.log.printmodel.layout.LayoutRow;

import java.util.List;

/**
 * @author erokhins
 */
public class LayoutTestUtils {
  public static String toShortStr(Node node) {
    return Integer.toHexString(node.getCommitIndex());
  }

  public static String toShortStr(GraphElement element) {
    Node node = element.getNode();
    if (node != null) {
      return toShortStr(node);
    }
    else {
      Edge edge = element.getEdge();
      if (edge == null) {
        throw new IllegalStateException();
      }
      return toShortStr(edge.getUpNode()) + ":" + toShortStr(edge.getDownNode());
    }
  }

  public static String toStr(LayoutRow row) {
    List<GraphElement> orderedGraphElements = row.getOrderedGraphElements();
    if (orderedGraphElements.isEmpty()) {
      return "";
    }
    StringBuilder s = new StringBuilder();
    s.append(toShortStr(orderedGraphElements.get(0)));
    for (int i = 1; i < orderedGraphElements.size(); i++) {
      s.append(" ").append(toShortStr(orderedGraphElements.get(i)));
    }
    return s.toString();
  }

  public static String toStr(LayoutModel layoutModel) {
    List<LayoutRow> cells = layoutModel.getLayoutRows();
    if (cells.isEmpty()) {
      return "";
    }
    StringBuilder s = new StringBuilder();
    s.append(toStr(cells.get(0)));
    for (int i = 1; i < cells.size(); i++) {
      s.append("\n").append(toStr(cells.get(i)));
    }

    return s.toString();
  }
}
