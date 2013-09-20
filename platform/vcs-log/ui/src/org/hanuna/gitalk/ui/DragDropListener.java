package org.hanuna.gitalk.ui;

import org.hanuna.gitalk.graph.elements.Node;

import java.awt.event.MouseEvent;
import java.util.List;

public class DragDropListener {

  public static final DragDropListener EMPTY = new DragDropListener();

  public static class Handler {

    private static final Handler EMPTY = new Handler();

    public void above(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {
    }

    public void below(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {

    }

    public void over(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {

    }

    public void overNode(int rowIndex, Node commit, MouseEvent e, List<Node> commitsBeingDragged) {

    }
  }

  public Handler drag() {
    return Handler.EMPTY;
  }

  public Handler drop() {
    return Handler.EMPTY;
  }

  public void draggingStarted(List<Node> commitsBeingDragged) {

  }

  public void draggingCanceled(List<Node> commitsBeingDragged) {

  }

  // This method totally should not be here
  public void reword(int row, String message) {

  }

}
