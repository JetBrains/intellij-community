package com.intellij.vcs.log.graph.mutable.elements;

import com.intellij.util.SmartList;
import com.intellij.vcs.log.graph.elements.Branch;
import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public class MutableNode implements Node {
  private final Branch branch;
  private final int hash;
  private MutableNodeRow nodeRow = null;
  private NodeType type;

  private final List<Edge> upEdges = new SmartList<Edge>();
  private final List<Edge> downEdges = new SmartList<Edge>();

  public MutableNode(Branch branch, int hash) {
    this.branch = branch;
    this.hash = hash;
  }

  @NotNull
  public List<Edge> getInnerUpEdges() {
    return upEdges;
  }

  @NotNull
  public List<Edge> getInnerDownEdges() {
    return downEdges;
  }

  @Override
  public int getRowIndex() {
    return nodeRow.getRowIndex();
  }

  public void setNodeRow(MutableNodeRow nodeRow) {
    this.nodeRow = nodeRow;
  }

  public void setType(NodeType type) {
    this.type = type;
  }

  @NotNull
  @Override
  public NodeType getType() {
    return type;
  }


  @NotNull
  @Override
  public List<Edge> getUpEdges() {
    return nodeRow.getGraphDecorator().getUpEdges(this, upEdges);
        /*
        GraphDecorator decorator = nodeRow.getGraphDecorator();
        Edge edge = decorator.getHideFragmentUpEdge(this);
        if (edge != null) {
            return OneElementList.buildList(edge);
        }

        List<Edge> visibleEdge = new ArrayList<Edge>();
        for (Edge upEdge : upEdges) {
            if (decorator.isVisibleNode((MutableNode) upEdge.getUpNode())) {
                visibleEdge.add(upEdge);
            }
        }
        return visibleEdge;
        */
  }

  @NotNull
  @Override
  public List<Edge> getDownEdges() {
    return nodeRow.getGraphDecorator().getDownEdges(this, downEdges);
        /*
        GraphDecorator decorator = nodeRow.getGraphDecorator();
        Edge edge = decorator.getHideFragmentDownEdge(this);
        if (edge != null) {
            return OneElementList.buildList(edge);
        }
        /*
        slow method:
        List<Edge> visibleEdge = new ArrayList<Edge>();
        for (Edge downEdge : downEdges) {
            if (decorator.isVisibleNode(downEdge.getDownNode())) {
                visibleEdge.add(downEdge);
            }
        }
        return visibleEdge;
        return Collections.unmodifiableList(downEdges);
        */
  }

  @Override
  public int getCommitIndex() {
    return hash;
  }

  @NotNull
  @Override
  public Branch getBranch() {
    return branch;
  }

  @Override
  public Node getNode() {
    return this;
  }

  @Override
  public Edge getEdge() {
    return null;
  }

  @Override
  public String toString() {
    return getType() + " " + getCommitIndex();
  }
}
