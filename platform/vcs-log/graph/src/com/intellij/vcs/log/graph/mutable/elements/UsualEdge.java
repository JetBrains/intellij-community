package com.intellij.vcs.log.graph.mutable.elements;

import com.intellij.vcs.log.graph.elements.Branch;
import com.intellij.vcs.log.graph.elements.Edge;
import com.intellij.vcs.log.graph.elements.Node;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class UsualEdge implements Edge {
  private final Node upNode;
  private final Node downNode;
  private final Branch branch;

  public UsualEdge(Node upNode, Node downNode, Branch branch) {
    this.upNode = upNode;
    this.downNode = downNode;
    this.branch = branch;
  }

  @NotNull
  @Override
  public Node getUpNode() {
    return upNode;
  }

  @NotNull
  @Override
  public Node getDownNode() {
    return downNode;
  }

  @NotNull
  @Override
  public EdgeType getType() {
    return EdgeType.USUAL;
  }

  @NotNull
  @Override
  public Branch getBranch() {
    return branch;
  }

  @Override
  public Node getNode() {
    return null;
  }

  @Override
  public Edge getEdge() {
    return this;
  }
}
