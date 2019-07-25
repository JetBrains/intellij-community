// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.awt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class RelativePoint {
  @NotNull
  private final Component myComponent;
  @NotNull
  private final Point myPointOnComponent;

  @NotNull
  private final Component myOriginalComponent;
  @NotNull
  private final Point myOriginalPoint;

  public RelativePoint(@NotNull MouseEvent event) {
    this(event.getComponent(), event.getPoint());
  }

  public RelativePoint(@NotNull Point screenPoint) {
    this(getTargetWindow(), calcPoint(screenPoint));
  }

  @NotNull
  private static Point calcPoint(@NotNull Point screenPoint) {
    Point p = new Point(screenPoint.x, screenPoint.y);
    SwingUtilities.convertPointFromScreen(p, getTargetWindow());
    return p;
  }

  @NotNull
  private static Window getTargetWindow() {
    Window[] windows = Window.getWindows();
    Window targetWindow = null;
    for (Window each : windows) {
      if (each.isActive()) {
        targetWindow = each;
        break;
      }
    }

    if (targetWindow == null) {
      targetWindow = JOptionPane.getRootFrame();
    }
    return targetWindow;
  }

  public RelativePoint(@NotNull Component aComponent, @NotNull Point aPointOnComponent) {
    JRootPane rootPane = SwingUtilities.getRootPane(aComponent);
    if (aComponent.isShowing() && rootPane != null) {
      myComponent = rootPane;
      myPointOnComponent = SwingUtilities.convertPoint(aComponent, aPointOnComponent, myComponent);
    }
    else {
      myComponent = aComponent;
      myPointOnComponent = aPointOnComponent;
    }
    myOriginalComponent = aComponent;
    myOriginalPoint = aPointOnComponent;
  }

  @NotNull
  public Component getComponent() {
    return myComponent;
  }

  public Point getPoint() {
    return myPointOnComponent;
  }

  public Point getPoint(@Nullable Component aTargetComponent) {
    //todo: remove that after implementation of DND to html design time controls
    boolean window = aTargetComponent instanceof Window;
    if (aTargetComponent == null || !window && (aTargetComponent.getParent() == null || SwingUtilities.getWindowAncestor(aTargetComponent) == null)) {
      return new Point();
    }

    return SwingUtilities.convertPoint(getComponent(), getPoint(), aTargetComponent);
  }

  @NotNull
  public RelativePoint getPointOn(@NotNull Component aTargetComponent) {
    final Point point = getPoint(aTargetComponent);
    return new RelativePoint(aTargetComponent, point);
  }

  @NotNull
  public Point getScreenPoint() {
    final Point point = (Point) getPoint().clone();
    SwingUtilities.convertPointToScreen(point, getComponent());
    return point;
  }

  @NotNull
  public MouseEvent toMouseEvent() {
    return new MouseEvent(myComponent, 0, 0, 0, myPointOnComponent.x, myPointOnComponent.y, 1, false);
  }

  @Override
  @NotNull
  public String toString() {
    //noinspection HardCodedStringLiteral
    return getPoint() + " on " + getComponent();
  }

  @NotNull
  public static RelativePoint getCenterOf(@NotNull JComponent component) {
    final Rectangle visibleRect = component.getVisibleRect();
    final Point point = new Point(visibleRect.x + visibleRect.width/2, visibleRect.y + visibleRect.height/2);
    return new RelativePoint(component, point);
  }

  @NotNull
  public static RelativePoint getSouthEastOf(@NotNull JComponent component) {
    final Rectangle visibleRect = component.getVisibleRect();
    final Point point = new Point(visibleRect.x + visibleRect.width, visibleRect.y + visibleRect.height);
    return new RelativePoint(component, point);
  }

  @NotNull
  public static RelativePoint getSouthWestOf(@NotNull JComponent component) {
    final Rectangle visibleRect = component.getVisibleRect();
    final Point point = new Point(visibleRect.x, visibleRect.y + visibleRect.height);
    return new RelativePoint(component, point);
  }

  @NotNull
  public static RelativePoint getSouthOf(@NotNull JComponent component) {
    final Rectangle visibleRect = component.getVisibleRect();
    final Point point = new Point(visibleRect.x + visibleRect.width / 2, visibleRect.y + visibleRect.height);
    return new RelativePoint(component, point);
  }

  @NotNull
  public static RelativePoint getNorthWestOf(@NotNull JComponent component) {
    final Rectangle visibleRect = component.getVisibleRect();
    final Point point = new Point(visibleRect.x, visibleRect.y);
    return new RelativePoint(component, point);
  }

  @NotNull @SuppressWarnings("unused")
  public static RelativePoint getNorthEastOf(@NotNull JComponent component) {
    final Rectangle visibleRect = component.getVisibleRect();
    final Point point = new Point(visibleRect.x + visibleRect.width, visibleRect.y);
    return new RelativePoint(component, point);
  }

  @NotNull
  public static RelativePoint fromScreen(Point screenPoint) {
    Frame root = JOptionPane.getRootFrame();
    SwingUtilities.convertPointFromScreen(screenPoint, root);
    return new RelativePoint(root, screenPoint);
  }

  @NotNull
  public Component getOriginalComponent() {
    return myOriginalComponent;
  }

  @NotNull
  public Point getOriginalPoint() {
    return myOriginalPoint;
  }
}
