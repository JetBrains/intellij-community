package com.intellij.vcs.log.printmodel;

import com.intellij.vcs.log.graph.elements.Edge;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class ShortEdge {
  private final Edge edge;
  private final int upPosition;
  private final int downPosition;
  private final boolean selected;
  private final boolean marked;

  public ShortEdge(@NotNull Edge edge, int upPosition, int downPosition, boolean selected, boolean marked) {
    this.edge = edge;
    this.upPosition = upPosition;
    this.downPosition = downPosition;
    this.selected = selected;
    this.marked = marked;
  }

  @NotNull
  public Edge getEdge() {
    return edge;
  }

  public boolean isMarked() {
    return marked;
  }

  public boolean isUsual() {
    return edge.getType() == Edge.EdgeType.USUAL;
  }

  public int getUpPosition() {
    return upPosition;
  }

  public boolean isSelected() {
    return selected;
  }

  public int getDownPosition() {
    return downPosition;
  }
}
