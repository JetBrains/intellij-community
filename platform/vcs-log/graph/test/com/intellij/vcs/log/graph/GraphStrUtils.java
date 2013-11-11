package com.intellij.vcs.log.graph;

import com.intellij.vcs.log.graph.elements.Branch;
import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.Node;
import com.intellij.vcs.log.graph.elements.NodeRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class GraphStrUtils {

  public static String toStr(Branch branch) {
    if (branch.getUpCommitHash() == branch.getDownCommitHash()) {
      return Integer.toHexString(branch.getUpCommitHash());
    }
    else {
      return Integer.toHexString(branch.getUpCommitHash()) + '#' + Integer.toHexString(branch.getDownCommitHash());
    }
  }

  /**
   * @return example:
   *         a0:a1:USUAL:a0
   *         up:down:type:branch
   */
  public static String toStr(Edge edge) {
    StringBuilder s = new StringBuilder();
    s.append(Integer.toHexString(edge.getUpNode().getCommitIndex())).append(":");
    s.append(Integer.toHexString(edge.getDownNode().getCommitIndex())).append(":");
    s.append(edge.getType()).append(":");
    s.append(toStr(edge.getBranch()));
    return s.toString();
  }

  public static String toStr(List<Edge> edges) {
    StringBuilder s = new StringBuilder();
    List<String> edgeStrings = new ArrayList<String>();
    for (Edge edge : edges) {
      edgeStrings.add(toStr(edge));
    }

    Collections.sort(edgeStrings);
    if (edgeStrings.size() > 0) {
      s.append(edgeStrings.get(0));
    }
    for (int i = 1; i < edges.size(); i++) {
      s.append(" ").append(edgeStrings.get(i));
    }
    return s.toString();
  }


  /**
   * @return example:
   *         a0|-|-a0:a1:USUAL:a0|-COMMIT_NODE|-a0|-0
   *         <p/>
   *         explanation:
   *         getCommitHash|-upEdges|-downEdges|-NodeType|-branch|-rowIndex
   */

  public static String toStr(Node node) {
    StringBuilder s = new StringBuilder();
    s.append(Integer.toHexString(node.getCommitIndex())).append("|-");
    s.append(toStr(node.getUpEdges())).append("|-");
    s.append(toStr(node.getDownEdges())).append("|-");
    s.append(node.getType()).append("|-");
    s.append(toStr(node.getBranch())).append("|-");
    s.append(node.getRowIndex());
    return s.toString();
  }

  public static String toStr(NodeRow row) {
    StringBuilder s = new StringBuilder();
    List<Node> nodes = row.getNodes();
    List<String> nodesString = new ArrayList<String>();
    for (Node node : nodes) {
      nodesString.add(toStr(node));
    }
    Collections.sort(nodesString);

    if (nodesString.size() > 0) {
      s.append(nodesString.get(0));
    }
    for (int i = 1; i < nodesString.size(); i++) {
      s.append("\n   ").append(nodesString.get(i));
    }
    return s.toString();
  }

  /**
   * @return textOfGraph in next format:
   *         every row in separate line, if in row more that 1 node:
   *         ...
   *         first node text row
   *         next node text row
   *         next node text row
   *         next row ...
   */

  public static String toStr(Graph graph) {
    StringBuilder s = new StringBuilder();
    List<NodeRow> rows = graph.getNodeRows();
    if (rows.size() > 0) {
      s.append(toStr(rows.get(0)));
    }
    for (int i = 1; i < rows.size(); i++) {
      s.append("\n").append(toStr(rows.get(i)));
    }
    return s.toString();
  }


}
