package com.intellij.vcs.log.graph.elements;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface Node extends GraphElement {

  int getRowIndex();

  @NotNull
  NodeType getType();

  @NotNull
  List<Edge> getUpEdges();

  @NotNull
  List<Edge> getDownEdges();

  /**
   * @return if type == COMMIT_NODE - this commit.
   *         if type == EDGE_NODE - common Parent
   *         if type == END_COMMIT_NODE - parent of This Commit
   */
  int getCommitIndex();

  enum NodeType {
    COMMIT_NODE,
    EDGE_NODE,
    END_COMMIT_NODE
  }

}
