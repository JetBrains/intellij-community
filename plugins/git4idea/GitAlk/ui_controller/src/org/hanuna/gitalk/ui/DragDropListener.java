package org.hanuna.gitalk.ui;

import org.hanuna.gitalk.graph.elements.Node;

import java.awt.event.MouseEvent;

public class DragDropListener {

  public static final DragDropListener EMPTY = new DragDropListener();

  public static class Handler {

    private static final Handler EMPTY = new Handler();

    public void above(int rowIndex, Node commit, MouseEvent e, Node commitBeingDragged) {
    }

    public void below(int rowIndex, Node commit, MouseEvent e, Node commitBeingDragged) {

    }

    public void over(int rowIndex, Node commit, MouseEvent e, Node commitBeingDragged) {

    }

    public void overNode(int rowIndex, Node commit, MouseEvent e, Node commitBeingDragged) {

    }
  }

  public Handler drag() {
    return Handler.EMPTY;
  }

  public Handler drop() {
    return Handler.EMPTY;
  }

  public void draggingStarted(Node commitBeingDragged) {

  }

  public void draggingCanceled(Node commitBeingDragged) {

  }

}
