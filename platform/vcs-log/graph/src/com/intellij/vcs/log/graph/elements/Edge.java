package com.intellij.vcs.log.graph.elements;

import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface Edge extends GraphElement {

  @NotNull
  public Node getUpNode();

  @NotNull
  public Node getDownNode();

  @NotNull
  public EdgeType getType();

  public static enum EdgeType {
    USUAL,
    HIDE_FRAGMENT
  }
}
