package com.intellij.vcs.log.graph.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author erokhins
 */
public interface GraphElement {

  @NotNull
  public Branch getBranch();

  /**
   * @return node, if this GraphElement was Node, another - null
   */
  @Nullable
  public Node getNode();

  /**
   * @return edge, if this GraphElement was Edge, another - null
   */
  @Nullable
  public Edge getEdge();

}
