// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.util.SystemInfo;
import org.intellij.lang.annotations.MagicConstant;
import org.junit.Assert;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public class EditorMouseFixture {
  private final EditorImpl myEditor;
  private int myX;
  private int myY;
  @MagicConstant(flagsFromClass = InputEvent.class)
  private int myModifiers;
  private int myButton = MouseEvent.BUTTON1;
  private int myLastId;
  private int myLastClickCount;
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
    return pressAt(myEditor.getGutterComponentEx(), 1, new Point(myEditor.getGutterComponentEx().getLineNumberAreaOffset(), myEditor.visualLineToY(visualLine)));
  }

  private EditorMouseFixture pressAt(int clickCount, Point p) {
    JComponent component = myEditor.getContentComponent();
    return pressAt(component, clickCount, p);
  }

  private EditorMouseFixture pressAt(Component component, int clickCount, Point p) {
    component.dispatchEvent(new MouseEvent(myLastComponent = component,
                                           myLastId = MouseEvent.MOUSE_PRESSED,
                                           System.currentTimeMillis(),
                                           myModifiers | getModifiersForButtonPress(myButton),
                                           myX = p.x,
                                           myY = p.y,
                                           myLastClickCount = clickCount,
                                           false, // Windows behaviour
                                           myButton));
    return this;
  }

  public EditorMouseFixture release() {
    int oldLastId = myLastId;
    int clickCount = myLastId == MouseEvent.MOUSE_PRESSED ? myLastClickCount : 0;
    myLastComponent.dispatchEvent(new MouseEvent(myLastComponent,
                                                 myLastId = MouseEvent.MOUSE_RELEASED,
                                                 System.currentTimeMillis(),
                                                 myModifiers | getModifiersForButtonRelease(myButton),
                                                 myX,
                                                 myY,
                                                 myLastClickCount = clickCount,
                                                 myButton == MouseEvent.BUTTON3, // Windows behaviour
                                                 myButton));
    if (oldLastId == MouseEvent.MOUSE_PRESSED) {
      myLastComponent.dispatchEvent(new MouseEvent(myLastComponent,
                                                   myLastId = MouseEvent.MOUSE_CLICKED,
                                                   System.currentTimeMillis(),
                                                   myModifiers | getModifiersForButtonRelease(myButton),
                                                   myX,
                                                   myY,
                                                   clickCount,
                                                   false, // Windows behaviour
                                                   myButton));
    }
    myLastComponent = null;
    return this;
  }

  public EditorMouseFixture clickAtXY(int x, int y) {
    return pressAtXY(x, y).release();
  }

  public EditorMouseFixture clickAt(int visualLine, int visualColumn) {
    return pressAt(visualLine, visualColumn).release();
  }

  public EditorMouseFixture doubleClickAt(int visualLine, int visualColumn) {
    return doubleClickNoReleaseAt(visualLine, visualColumn).release();
  }

  public EditorMouseFixture doubleClickNoReleaseAt(int visualLine, int visualColumn) {
    return clickAt(visualLine, visualColumn).pressAt(2, getPoint(visualLine, visualColumn));
  }

  public EditorMouseFixture tripleClickAt(int visualLine, int visualColumn) {
    return doubleClickAt(visualLine, visualColumn).pressAt(3, getPoint(visualLine, visualColumn)).release();
  }

  public EditorMouseFixture moveTo(int visualLine, int visualColumn) {
    Point p = getPoint(visualLine, visualColumn);
    return moveToXY(p.x, p.y);
  }

  public EditorMouseFixture dragTo(int visualLine, int visualColumn) {
    Point p = getPoint(visualLine, visualColumn);
    return dragToXY(p.x, p.y);
  }

  public EditorMouseFixture dragToLineNumbers(int visualLine) {
    assert myEditor.getSettings().isLineNumbersShown();
    return dragToXY(myEditor.getGutterComponentEx(), 0, myEditor.visualLineToY(visualLine));
  }

  public EditorMouseFixture moveToXY(int x, int y) {
    Component component = myEditor.getContentComponent();
    component.dispatchEvent(new MouseEvent(component,
                                           myLastId = MouseEvent.MOUSE_MOVED,
                                           System.currentTimeMillis(),
                                           myModifiers,
                                           myX = x,
                                           myY = y,
                                           myLastClickCount = 0,
                                           false,
                                           0));
    return this;
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
                                           myModifiers | getModifiersForButtonPress(myButton),
                                           myX = x,
                                           myY = y,
                                           myLastClickCount = 1,
                                           false,
                                           0));
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

  @MagicConstant(flagsFromClass = InputEvent.class)
  private static int getModifiersForButtonPress(int button) {
    return switch (button) {
      case MouseEvent.BUTTON1 -> InputEvent.BUTTON1_DOWN_MASK;
      case MouseEvent.BUTTON2 -> InputEvent.BUTTON2_DOWN_MASK;
      case MouseEvent.BUTTON3 -> InputEvent.BUTTON3_DOWN_MASK;
      default -> 0;
    };
  }

  @MagicConstant(flagsFromClass = InputEvent.class)
  private static int getModifiersForButtonRelease(int button) {
    return switch (button) {
      case MouseEvent.BUTTON2 -> InputEvent.ALT_DOWN_MASK;
      case MouseEvent.BUTTON3 -> InputEvent.META_DOWN_MASK;
      default -> 0;
    };
  }
}
