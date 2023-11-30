// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.awt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class RelativePoint {
  private final @NotNull Component myComponent;
  private final @NotNull Point myPointOnComponent;

  private final @NotNull Component myOriginalComponent;
  private final @NotNull Point myOriginalPoint;

  public RelativePoint(@NotNull MouseEvent event) {
    this(event.getComponent(), event.getPoint());
  }

  public RelativePoint(@NotNull Point screenPoint) {
    this(getTargetWindow(), calcPoint(screenPoint));
  }

  private static @NotNull Point calcPoint(@NotNull Point screenPoint) {
    Point p = new Point(screenPoint.x, screenPoint.y);
    SwingUtilities.convertPointFromScreen(p, getTargetWindow());
    return p;
  }

  private static @NotNull Window getTargetWindow() {
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

  public @NotNull Component getComponent() {
    return myComponent;
  }

  public Point getPoint() {
    return myPointOnComponent;
  }

  public @NotNull Point getPoint(@Nullable Component aTargetComponent) {
    //todo: remove that after implementation of DND to html design time controls
    boolean window = aTargetComponent instanceof Window;
    if (aTargetComponent == null || !window && (aTargetComponent.getParent() == null || SwingUtilities.getWindowAncestor(aTargetComponent) == null)) {
      return new Point();
    }

    return SwingUtilities.convertPoint(getComponent(), getPoint(), aTargetComponent);
  }

  public @NotNull RelativePoint getPointOn(@NotNull Component aTargetComponent) {
    final Point point = getPoint(aTargetComponent);
    return new RelativePoint(aTargetComponent, point);
  }

  public @NotNull Point getScreenPoint() {
    final Point point = (Point) getPoint().clone();
    SwingUtilities.convertPointToScreen(point, getComponent());
    return point;
  }

  public @NotNull MouseEvent toMouseEvent() {
    return new MouseEvent(myComponent, 0, 0, 0, myPointOnComponent.x, myPointOnComponent.y, 1, false);
  }

  @Override
  public @NotNull String toString() {
    //noinspection HardCodedStringLiteral
    return getPoint() + " on " + getComponent();
  }

  public static @NotNull RelativePoint getCenterOf(@NotNull JComponent component) {
    return new AnchoredPoint(AnchoredPoint.Anchor.CENTER, component);
  }

  public static @NotNull RelativePoint getSouthEastOf(@NotNull JComponent component) {
    return new AnchoredPoint(AnchoredPoint.Anchor.BOTTOM_RIGHT, component);
  }

  public static @NotNull RelativePoint getSouthWestOf(@NotNull JComponent component) {
    return new AnchoredPoint(AnchoredPoint.Anchor.BOTTOM_LEFT, component);
  }

  public static @NotNull RelativePoint getSouthOf(@NotNull JComponent component) {
    return new AnchoredPoint(AnchoredPoint.Anchor.BOTTOM, component);
  }

  public static @NotNull RelativePoint getNorthWestOf(@NotNull JComponent component) {
    return new AnchoredPoint(AnchoredPoint.Anchor.TOP_LEFT, component);
  }

  public static @NotNull RelativePoint getNorthEastOf(@NotNull JComponent component) {
    return new AnchoredPoint(AnchoredPoint.Anchor.TOP_RIGHT, component);
  }

  public static @NotNull RelativePoint fromScreen(Point screenPoint) {
    Frame root = JOptionPane.getRootFrame();
    SwingUtilities.convertPointFromScreen(screenPoint, root);
    return new RelativePoint(root, screenPoint);
  }

  public @NotNull Component getOriginalComponent() {
    return myOriginalComponent;
  }

  public @NotNull Point getOriginalPoint() {
    return myOriginalPoint;
  }
}
