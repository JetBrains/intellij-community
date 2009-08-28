/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ui;

import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

/**
 * User: anna
 * Date: 13-Mar-2006
 */
public class MoveComponentListener extends MouseAdapter implements MouseMotionListener {
  private final CaptionPanel myComponent;
  private Point myStartPoint = null;

  public MoveComponentListener(final CaptionPanel component) {
    myComponent = component;
  }

  private void endOperation() {
    AbstractPopup.setDefaultCursor(myComponent);
    myStartPoint = null;
  }

  public void mousePressed(MouseEvent e) {
    myStartPoint = new RelativePoint(e).getScreenPoint();
    final Point titleOffset = RelativePoint.getNorthWestOf(myComponent).getScreenPoint();
    myStartPoint.x -= titleOffset.x;
    myStartPoint.y -= titleOffset.y;
  }

  public void mouseClicked(MouseEvent e) {
    endOperation();
  }

  public void mouseReleased(MouseEvent e) {
    endOperation();
  }

  public void mouseMoved(MouseEvent e) {
    if (e.isConsumed()) return;
    AbstractPopup.setDefaultCursor(myComponent);
  }

  public void mouseDragged(MouseEvent e) {
    if (e.isConsumed()) return;
    AbstractPopup.setDefaultCursor(myComponent);
    if (myStartPoint != null) {
      final Point draggedTo = new RelativePoint(e).getScreenPoint();
      draggedTo.x -= myStartPoint.x;
      draggedTo.y -= myStartPoint.y;

      AbstractPopup.moveTo(myComponent, draggedTo, null);

      e.consume();
    }
  }
}
