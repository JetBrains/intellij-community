/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ui;

import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.AbstractPopup;
import org.intellij.lang.annotations.JdkConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

/**
 * User: anna
 * Date: 13-Mar-2006
 */
public class ResizeComponentListener extends MouseAdapter implements MouseMotionListener {
  private static final int SENSITIVITY = 4;
  private final AbstractPopup myPopup;
  private final AbstractPopup.MyContentPanel myComponent;
  private Point myStartPoint = null;
  @JdkConstants.CursorType private int myDirection = -1;
  private final IdeGlassPane myGlassPane;

  public ResizeComponentListener(final AbstractPopup popup, IdeGlassPane glassPane) {
    myPopup = popup;
    myComponent = (AbstractPopup.MyContentPanel)popup.getContent();
    myGlassPane = glassPane;
  }

  public void mousePressed(MouseEvent e) {
    final Window popupWindow = SwingUtilities.windowForComponent(myComponent);
    if (popupWindow != null) {
      myStartPoint = new RelativePoint(e).getScreenPoint();
      myDirection = getDirection(myStartPoint, popupWindow.getBounds());
      if (myDirection == Cursor.DEFAULT_CURSOR){
        myStartPoint = null;
      } else {
        if (isToShowBorder()) {
          myComponent.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, Color.black.brighter()));
        }
      }
    }
  }

  public void mouseClicked(MouseEvent e) {
    endOperation();
  }

  public void mouseReleased(MouseEvent e) {
    endOperation();
  }


  public void mouseExited(MouseEvent e) {
    final Window popupWindow = SwingUtilities.windowForComponent(myComponent);
    if (popupWindow == null) return;
    clearBorder(popupWindow);
  }

  private void endOperation() {
    final Window popupWindow = SwingUtilities.windowForComponent(myComponent);
    if (popupWindow != null) {
      if (isToShowBorder()) {
        myComponent.setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
      }

      Dimension size = popupWindow.getSize();
      Dimension minSize = popupWindow.getMinimumSize();
      if (size.width < minSize.width) {
        size.width = minSize.width;
      }
      if (size.height < minSize.height) {
        size.height = minSize.height;
      }

      popupWindow.setSize(size);

      popupWindow.validate();
      popupWindow.repaint();
      setWindowCursor(Cursor.DEFAULT_CURSOR);
      myPopup.storeDimensionSize(popupWindow.getSize());
    }
    myStartPoint = null;
    myDirection = Cursor.CUSTOM_CURSOR;
  }

  private boolean isToShowBorder() {
    return false;
  }

  private void doResize(final Point point) {
    final Window popupWindow = SwingUtilities.windowForComponent(myComponent);
    final Rectangle bounds = popupWindow.getBounds();
    final Point location = popupWindow.getLocation();
    switch (myDirection){
      case Cursor.NW_RESIZE_CURSOR :
        popupWindow.setBounds(location.x + point.x - myStartPoint.x,
                              location.y + point.y - myStartPoint.y,
                              bounds.width + myStartPoint.x - point.x,
                              bounds.height + myStartPoint.y - point.y );
        break;
      case Cursor.N_RESIZE_CURSOR :
        popupWindow.setBounds(location.x,
                              location.y + point.y - myStartPoint.y,
                              bounds.width,
                              bounds.height + myStartPoint.y - point.y);
        break;
      case Cursor.NE_RESIZE_CURSOR :
        popupWindow.setBounds(location.x,
                              location.y + point.y - myStartPoint.y,
                              bounds.width + point.x - myStartPoint.x,
                              bounds.height + myStartPoint.y - point.y);
        break;
      case Cursor.E_RESIZE_CURSOR :
        popupWindow.setBounds(location.x ,
                              location.y,
                              bounds.width + point.x - myStartPoint.x,
                              bounds.height);
        break;
      case Cursor.SE_RESIZE_CURSOR :
        popupWindow.setBounds(location.x,
                              location.y,
                              bounds.width + point.x - myStartPoint.x,
                              bounds.height + point.y - myStartPoint.y);
        break;
      case Cursor.S_RESIZE_CURSOR :
        popupWindow.setBounds(location.x,
                              location.y,
                              bounds.width ,
                              bounds.height + point.y - myStartPoint.y);
        break;
      case Cursor.SW_RESIZE_CURSOR :
        popupWindow.setBounds(location.x + point.x - myStartPoint.x,
                              location.y,
                              bounds.width + myStartPoint.x - point.x,
                              bounds.height + point.y - myStartPoint.y);
        break;
      case Cursor.W_RESIZE_CURSOR :
        popupWindow.setBounds(location.x + point.x - myStartPoint.x,
                              location.y,
                              bounds.width + myStartPoint.x - point.x,
                              bounds.height);
        break;
    }

    popupWindow.validate();
  }

  public void mouseMoved(MouseEvent e) {
    Point point = new RelativePoint(e).getScreenPoint();
    final Window popupWindow = SwingUtilities.windowForComponent(myComponent);
    if (popupWindow == null) return;
    final int cursor = getDirection(point, popupWindow.getBounds());
    if (cursor != Cursor.DEFAULT_CURSOR){
      if (isToShowBorder()) {
        if (myStartPoint == null) {
          myComponent.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, Color.black.brighter()));
        }
      }
      setWindowCursor(cursor);
      e.consume();
    } else {
      clearBorder(popupWindow);
    }
  }

  private void setWindowCursor(@JdkConstants.CursorType int cursor) {
    myGlassPane.setCursor(Cursor.getPredefinedCursor(cursor), this);
  }

  private void clearBorder(final Window popupWindow) {
    if (isToShowBorder()){
      myComponent.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }
    setWindowCursor(Cursor.DEFAULT_CURSOR);
  }

  public void mouseDragged(MouseEvent e) {
    if (e.isConsumed()) return;
    final Point point = new RelativePoint(e).getScreenPoint();
    final Window popupWindow = SwingUtilities.windowForComponent(myComponent);
    if (popupWindow == null) return;
    if (myStartPoint != null) {
      if (isToShowBorder()) {
        setWindowCursor(myDirection);
      }
      doResize(point);
      myStartPoint = point;
      e.consume();
    } else {
      if (isToShowBorder()) {
        final int cursor = getDirection(point, popupWindow.getBounds());
        setWindowCursor(cursor);
      }
    }
  }

  @JdkConstants.CursorType
  private int getDirection(Point startPoint, Rectangle bounds){
    if (myPopup.isToDrawMacCorner()){
      if (bounds.x + bounds.width - startPoint.x < 16 && //inside icon
          bounds.y + bounds.height - startPoint.y < 16 &&
          bounds.y + bounds.height - startPoint.y > 0 &&
          bounds.x + bounds.width - startPoint.x > 0){
        return Cursor.SE_RESIZE_CURSOR;
      }
    }
    bounds = new Rectangle(bounds.x + 2, bounds.y + 2, bounds.width - 2, bounds.height - 2);
    if (!bounds.contains(startPoint)){
      return Cursor.DEFAULT_CURSOR;
    }
    if (Math.abs(startPoint.x - bounds.x ) < SENSITIVITY){ //left bound
      if (Math.abs(startPoint.y - bounds.y) < SENSITIVITY){ //top
        return Cursor.NW_RESIZE_CURSOR;
      } else if (Math.abs(bounds.y + bounds.height - startPoint.y) < SENSITIVITY) { //bottom
        return Cursor.SW_RESIZE_CURSOR;
      } else { //edge
        return Cursor.W_RESIZE_CURSOR;
      }
    } else if (Math.abs(bounds.x + bounds.width - startPoint.x) < SENSITIVITY){ //right
      if (Math.abs(startPoint.y - bounds.y) < SENSITIVITY){ //top
        return Cursor.NE_RESIZE_CURSOR;
      } else if (Math.abs(bounds.y + bounds.height - startPoint.y) < SENSITIVITY) { //bottom
        return Cursor.SE_RESIZE_CURSOR;
      } else { //edge
        return Cursor.E_RESIZE_CURSOR;
      }
    } else { //other
      if (Math.abs(startPoint.y - bounds.y) < SENSITIVITY){ //top
        return Cursor.N_RESIZE_CURSOR;
      } else if (Math.abs(bounds.y + bounds.height - startPoint.y) < SENSITIVITY) { //bottom
        return Cursor.S_RESIZE_CURSOR;
      } else { //edge
        return Cursor.DEFAULT_CURSOR;
      }
    }
  }
}
