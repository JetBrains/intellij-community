// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.MenuDragMouseEvent;
import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.*;

public class MouseEventAdapter<T> extends MouseAdapter implements MouseInputListener {
  private final T myAdapter;

  public MouseEventAdapter(T adapter) {
    myAdapter = adapter;
  }

  @Override
  public void mouseEntered(MouseEvent event) {
    if (event == null || event.isConsumed()) return;
    MouseListener listener = getMouseListener(myAdapter);
    if (listener != null) listener.mouseEntered(convert(event));
  }

  @Override
  public void mousePressed(MouseEvent event) {
    if (event == null || event.isConsumed()) return;
    MouseListener listener = getMouseListener(myAdapter);
    if (listener != null) listener.mousePressed(convert(event));
  }

  @Override
  public void mouseClicked(MouseEvent event) {
    if (event == null || event.isConsumed()) return;
    MouseListener listener = getMouseListener(myAdapter);
    if (listener != null) listener.mouseClicked(convert(event));
  }

  @Override
  public void mouseReleased(MouseEvent event) {
    if (event == null || event.isConsumed()) return;
    MouseListener listener = getMouseListener(myAdapter);
    if (listener != null) listener.mouseReleased(convert(event));
  }

  @Override
  public void mouseExited(MouseEvent event) {
    if (event == null || event.isConsumed()) return;
    MouseListener listener = getMouseListener(myAdapter);
    if (listener != null) listener.mouseExited(convert(event));
  }

  @Override
  public void mouseMoved(MouseEvent event) {
    if (event == null || event.isConsumed()) return;
    MouseMotionListener listener = getMouseMotionListener(myAdapter);
    if (listener != null) listener.mouseMoved(convert(event));
  }

  @Override
  public void mouseDragged(MouseEvent event) {
    if (event == null || event.isConsumed()) return;
    MouseMotionListener listener = getMouseMotionListener(myAdapter);
    if (listener != null) listener.mouseDragged(convert(event));
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent event) {
    if (event == null || event.isConsumed()) return;
    MouseWheelListener listener = getMouseWheelListener(myAdapter);
    if (listener != null) listener.mouseWheelMoved(convertWheel(event));
  }

  protected MouseListener getMouseListener(T adapter) {
    return adapter instanceof MouseListener ? (MouseListener)adapter : null;
  }

  protected MouseMotionListener getMouseMotionListener(T adapter) {
    MouseListener listener = getMouseListener(adapter);
    return listener instanceof MouseMotionListener ? (MouseMotionListener)listener : null;
  }

  protected MouseWheelListener getMouseWheelListener(T adapter) {
    MouseListener listener = getMouseListener(adapter);
    return listener instanceof MouseWheelListener ? (MouseWheelListener)listener : null;
  }

  protected @NotNull MouseEvent convert(@NotNull MouseEvent event) {
    return event;
  }

  protected @NotNull MouseWheelEvent convertWheel(@NotNull MouseWheelEvent event) {
    return event;
  }

  public static @NotNull MouseEvent convert(@NotNull MouseEvent event, Component source) {
    Point point = event.getLocationOnScreen();
    SwingUtilities.convertPointFromScreen(point, source);
    return convert(event, source, point.x, point.y);
  }

  public static @NotNull MouseEvent convert(@NotNull MouseEvent event, Component source, int x, int y) {
    return convert(event, source, event.getID(), event.getWhen(), UIUtil.getAllModifiers(event), x, y);
  }

  public static @NotNull MouseEvent convert(@NotNull MouseEvent event, Component source, int id, long when, int modifiers, int x, int y) {
    if (event instanceof MouseWheelEvent) return convert((MouseWheelEvent)event, source, id, when, modifiers, x, y);
    if (event instanceof MenuDragMouseEvent) return convert((MenuDragMouseEvent)event, source, id, when, modifiers, x, y);
    return new MouseEvent(source, id, when, modifiers, x, y,
                          event.getClickCount(),
                          event.isPopupTrigger(),
                          event.getButton());
  }

  public static @NotNull MouseWheelEvent convert(@NotNull MouseWheelEvent event, Component source, int id, long when, int modifiers, int x, int y) {
    return new MouseWheelEvent(source, id, when, modifiers, x, y,
                               event.getXOnScreen(),
                               event.getYOnScreen(),
                               event.getClickCount(),
                               event.isPopupTrigger(),
                               event.getScrollType(),
                               event.getScrollAmount(),
                               event.getWheelRotation(),
                               event.getPreciseWheelRotation());
  }

  public static @NotNull MenuDragMouseEvent convert(MenuDragMouseEvent event, Component source, int id, long when, int modifiers, int x, int y) {
    return new MenuDragMouseEvent(source, id, when, modifiers, x, y,
                                  event.getClickCount(),
                                  event.isPopupTrigger(),
                                  event.getPath(),
                                  event.getMenuSelectionManager());
  }

  private static boolean dispatch(Component component, @NotNull MouseEvent event) {
    component.dispatchEvent(event);
    return event.isConsumed();
  }

  public static void redispatch(@NotNull MouseEvent event, Component source) {
    if (source != null && dispatch(source, convert(event, source))) event.consume();
  }

  public static void redispatch(@NotNull MouseEvent event, Component source, int x, int y) {
    if (source != null && dispatch(source, convert(event, source, x, y))) event.consume();
  }
}
