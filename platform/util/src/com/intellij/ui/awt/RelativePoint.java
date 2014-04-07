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
package com.intellij.ui.awt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class RelativePoint {

  private Component myComponent;
  private Point myPointOnComponent;

  private Component myOriginalComponent;
  private Point myOriginalPoint;

  public RelativePoint(@NotNull MouseEvent event) {
    init(event.getComponent(), event.getPoint());

    myOriginalComponent = event.getComponent();
    myOriginalPoint = event.getPoint();
  }

  public RelativePoint(@NotNull Component aComponent, Point aPointOnComponent) {
    init(aComponent, aPointOnComponent);
  }

  public RelativePoint(@NotNull Point screenPoint) {
    Point p = new Point(screenPoint.x, screenPoint.y);
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

    SwingUtilities.convertPointFromScreen(p, targetWindow);
    init(targetWindow, p);
  }

  private void init(@NotNull Component aComponent, Point aPointOnComponent) {
    if (aComponent.isShowing()) {
      myComponent = SwingUtilities.getRootPane(aComponent);
      myPointOnComponent = SwingUtilities.convertPoint(aComponent, aPointOnComponent, myComponent);
    }
    else {
      myComponent = aComponent;
      myPointOnComponent = aPointOnComponent;
    }

    myOriginalComponent = myComponent;
    myOriginalPoint = myPointOnComponent;
  }

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

  @NotNull
  public String toString() {
    //noinspection HardCodedStringLiteral
    return getPoint() + " on " + getComponent().toString();
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
  public static RelativePoint getNorthWestOf(@NotNull JComponent component) {
    final Rectangle visibleRect = component.getVisibleRect();
    final Point point = new Point(visibleRect.x, visibleRect.y);
    return new RelativePoint(component, point);
  }

  @NotNull
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

  public Component getOriginalComponent() {
    return myOriginalComponent;
  }

  public Point getOriginalPoint() {
    return myOriginalPoint;
  }
}
