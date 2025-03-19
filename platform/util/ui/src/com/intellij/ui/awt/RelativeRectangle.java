// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.awt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public final class RelativeRectangle {

  private final RelativePoint myPoint;
  private final Dimension myDimension;

  public RelativeRectangle() {
    this(new RelativePoint(new JLabel(), new Point()), new Dimension());
  }

  public RelativeRectangle(MouseEvent event, Dimension size) {
    this(event.getComponent(), new Rectangle(event.getPoint(), size));
  }

  public RelativeRectangle(JComponent component) {
    this(new RelativePoint(component.getParent(), component.getBounds().getLocation()), component.getBounds().getSize());
  }

  public RelativeRectangle(Component component, Rectangle rectangle) {
    this(new RelativePoint(component, rectangle.getLocation()), rectangle.getSize());
  }

  public RelativeRectangle(RelativePoint point, Dimension dimension) {
    myDimension = dimension;
    myPoint = point;
  }

  public Dimension getDimension() {
    return myDimension;
  }

  public RelativePoint getPoint() {
    return myPoint;
  }

  public RelativePoint getMaxPoint() {
    return new RelativePoint(myPoint.getComponent(),
        new Point(myPoint.getPoint().x + myDimension.width, myPoint.getPoint().y + myDimension.height));
  }

  public Rectangle getRectangleOn(Component target) {
    return new Rectangle(getPoint().getPoint(target), getDimension());
  }

  public Rectangle getScreenRectangle() {
    return new Rectangle(getPoint().getScreenPoint(), getDimension());
  }

  public static RelativeRectangle fromScreen(JComponent target, Rectangle screenRectangle) {
    Point relativePoint = screenRectangle.getLocation();
    SwingUtilities.convertPointFromScreen(relativePoint, target);
    return new RelativeRectangle(new RelativePoint(target, relativePoint), screenRectangle.getSize());
  }

  public Component getComponent() {
    return getPoint().getComponent();
  }

  /**
   * @deprecated Use the overload that takes a DevicePoint, which handles locations on different screens with different scaling factors
   */
  @Deprecated(forRemoval = true)
  public boolean contains(final RelativePoint relativePoint) {
    return contains(new DevicePoint(relativePoint));
  }

  public boolean contains(final DevicePoint devicePoint) {
    // Get this rectangle and the device point in their own screen coordinate systems. If the rectangle is going to contain the device
    // point, then they must be on the same screen, and will therefore be in the same coordinate system, and it's safe to compare.
    return getScreenRectangle().contains(devicePoint.getLocationOnScreen());
  }
}
