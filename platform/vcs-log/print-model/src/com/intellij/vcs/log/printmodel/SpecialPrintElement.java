package com.intellij.vcs.log.printmodel;

import com.intellij.vcs.log.graph.elements.GraphElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class SpecialPrintElement {
  private final GraphElement graphElement;
  private final int position;
  private final Type type;
  private final boolean selected;

  public boolean isMarked() {
    return marked;
  }

  private final boolean marked;
  private final int dragAndDropSelect;  // 0 -nothing -1 -down 1 - up

  public SpecialPrintElement(@NotNull GraphElement graphElement,
                             int position,
                             @NotNull Type type,
                             boolean selected,
                             boolean marked,
                             int dragAndDropSelect) {
    this.graphElement = graphElement;
    this.position = position;
    this.type = type;
    this.selected = selected;
    this.marked = marked;
    this.dragAndDropSelect = dragAndDropSelect;
  }

  @NotNull
  public GraphElement getGraphElement() {
    return graphElement;
  }

  public int getDragAndDropSelect() {
    return dragAndDropSelect;
  }

  public int getPosition() {
    return position;
  }

  public boolean isSelected() {
    return selected;
  }

  @NotNull
  public Type getType() {
    return type;
  }

  public static enum Type {
    COMMIT_NODE,
    UP_ARROW,
    DOWN_ARROW
  }
}
