package org.hanuna.gitalk.ui;

import org.hanuna.gitalk.commit.Hash;

import java.awt.event.MouseEvent;

public class DragDropListener {

  public static final DragDropListener EMPTY = new DragDropListener();

  public static class Handler {

    private static final Handler EMPTY = new Handler();

    public void above(int rowIndex, Hash commit, MouseEvent e, Hash commitBeingDragged) {
    }

    public void below(int rowIndex, Hash commit, MouseEvent e, Hash commitBeingDragged) {

    }

    public void over(int rowIndex, Hash commit, MouseEvent e, Hash commitBeingDragged) {

    }

    public void overNode(int rowIndex, Hash commit, MouseEvent e, Hash commitBeingDragged) {

    }
  }

  public Handler drag() {
    return Handler.EMPTY;
  }

  public Handler drop() {
    return Handler.EMPTY;
  }

  public void draggingStarted(Hash commitBeingDragged) {

  }

  public void draggingCanceled(Hash commitBeingDragged) {

  }

}
