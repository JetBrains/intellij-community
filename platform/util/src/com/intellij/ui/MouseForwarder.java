package com.intellij.ui;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class MouseForwarder implements MouseListener{
  @NotNull private Component myTarget;
  private boolean myLater;

  public static Disposable installOn(@NotNull final Component component, @NotNull Component target, boolean later) {
    final MouseForwarder forwarder = new MouseForwarder(target, later);
    component.addMouseListener(forwarder);
    return new Disposable() {
      @Override
      public void dispose() {
        component.removeMouseListener(forwarder);
      }
    };
  }

  private MouseForwarder(@NotNull Component target, boolean later) {
    myTarget = target;
    myLater = later;
  }
  private void redispatch(final MouseEvent e) {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        myTarget.dispatchEvent(SwingUtilities.convertMouseEvent(e.getComponent(), e, myTarget));
      }
    };
    if (myLater) {
      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(runnable);
    }
    else {
      runnable.run();
    }
  }

  @Override
  public void mouseClicked(MouseEvent e) {
    redispatch(e);
  }

  @Override
  public void mousePressed(MouseEvent e) {
    redispatch(e);
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    redispatch(e);
  }

  @Override
  public void mouseEntered(MouseEvent e) {
    redispatch(e);
  }

  @Override
  public void mouseExited(MouseEvent e) {
    redispatch(e);
  }
}
