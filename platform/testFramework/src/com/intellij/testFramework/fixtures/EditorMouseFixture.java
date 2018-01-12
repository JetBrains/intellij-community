/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.SystemInfo;
import org.junit.Assert;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

@SuppressWarnings("MagicConstant")
public class EditorMouseFixture {
  private final EditorImpl myEditor;
  private int myX;
  private int myY;
  private int myModifiers;
  private int myButton = MouseEvent.BUTTON1;
  private int myLastId;
  private Component myLastComponent;

  public EditorMouseFixture(EditorImpl editor) {
    myEditor = editor;
  }

  public EditorMouseFixture pressAtXY(int x, int y) {
    return pressAt(1, new Point(x, y));
  }

  public EditorMouseFixture pressAt(int visualLine, int visualColumn) {
    return pressAt(1, getPoint(visualLine, visualColumn));
  }

  public EditorMouseFixture pressAtLineNumbers(int visualLine) {
    assert myEditor.getSettings().isLineNumbersShown();
    return pressAt(myEditor.getGutterComponentEx(), 1, new Point(0, myEditor.visibleLineToY(visualLine)));
  }

  private EditorMouseFixture pressAt(int clickCount, Point p) {
    JComponent component = myEditor.getContentComponent();
    return pressAt(component, clickCount, p);
  }

  private EditorMouseFixture pressAt(Component component, int clickCount, Point p) {
    component.dispatchEvent(new MouseEvent(myLastComponent = component,
                                           myLastId = MouseEvent.MOUSE_PRESSED,
                                           System.currentTimeMillis(),
                                           getModifiers(),
                                           myX = p.x,
                                           myY = p.y,
                                           clickCount,
                                           false,
                                           myButton));
    return this;
  }

  public EditorMouseFixture release() {
    return release(1);
  }

  private EditorMouseFixture release(int clickCount) {
    int oldLastId = myLastId;
    myLastComponent.dispatchEvent(new MouseEvent(myLastComponent,
                                                 myLastId = MouseEvent.MOUSE_RELEASED,
                                                 System.currentTimeMillis(),
                                                 getModifiers(),
                                                 myX,
                                                 myY,
                                                 clickCount,
                                                 false,
                                                 myButton));
    if (oldLastId == MouseEvent.MOUSE_PRESSED) {
      myLastComponent.dispatchEvent(new MouseEvent(myLastComponent,
                                                   myLastId = MouseEvent.MOUSE_CLICKED,
                                                   System.currentTimeMillis(),
                                                   getModifiers(),
                                                   myX,
                                                   myY,
                                                   clickCount,
                                                   false,
                                                   myButton));
    }
    myLastComponent = null;
    return this;
  }

  public EditorMouseFixture clickAt(int visualLine, int visualColumn) {
    return pressAt(visualLine, visualColumn).release();
  }

  public EditorMouseFixture doubleClickAt(int visualLine, int visualColumn) {
    return clickAt(visualLine, visualColumn).pressAt(2, getPoint(visualLine, visualColumn)).release(2);
  }

  public EditorMouseFixture tripleClickAt(int visualLine, int visualColumn) {
    return doubleClickAt(visualLine, visualColumn).pressAt(3, getPoint(visualLine, visualColumn)).release(3);
  }

  public EditorMouseFixture dragTo(int visualLine, int visualColumn) {
    Point p = getPoint(visualLine, visualColumn);
    return dragToXY(p.x, p.y);
  }
  
  public EditorMouseFixture dragToLineNumbers(int visualLine) {
    assert myEditor.getSettings().isLineNumbersShown();
    return dragToXY(myEditor.getGutterComponentEx(), 0, myEditor.visibleLineToY(visualLine));
  }

  public EditorMouseFixture dragToXY(int x, int y) {
    Assert.assertFalse("Cannot test mouse dragging: editor visible size is not set. Use EditorTestUtil.setEditorVisibleSize(width, height)", 
                       myEditor.getScrollingModel().getVisibleArea().isEmpty());
    JComponent component = myEditor.getContentComponent();
    return dragToXY(component, x, y);
  }

  private EditorMouseFixture dragToXY(JComponent component, int x, int y) {
    component.dispatchEvent(new MouseEvent(component,
                                           myLastId = MouseEvent.MOUSE_DRAGGED,
                                           System.currentTimeMillis(),
                                           getModifiers(),
                                           myX = x,
                                           myY = y,
                                           1,
                                           false,
                                           myButton));
    return this;
  }

  public EditorMouseFixture ctrl() {
    myModifiers |= SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
    return this;
  }

  public EditorMouseFixture alt() {
    myModifiers |= InputEvent.ALT_DOWN_MASK;
    return this;
  }

  public EditorMouseFixture shift() {
    myModifiers |= InputEvent.SHIFT_DOWN_MASK;
    return this;
  }

  public EditorMouseFixture noModifiers() {
    myModifiers = 0;
    return this;
  }

  public EditorMouseFixture middle() {
    myButton = MouseEvent.BUTTON2;
    return this;
  }

  public EditorMouseFixture right() {
    myButton = MouseEvent.BUTTON3;
    return this;
  }

  private Point getPoint(int visualLine, int visualColumn) {
    return myEditor.visualPositionToXY(new VisualPosition(visualLine, visualColumn));
  }

  private int getModifiers() {
    if (myButton == MouseEvent.BUTTON3) {
      return myModifiers | InputEvent.META_MASK;
    }
    else if (myButton == MouseEvent.BUTTON2) {
      return myModifiers | InputEvent.ALT_MASK;
    }
    else {
      return myModifiers;
    }
  }
}
