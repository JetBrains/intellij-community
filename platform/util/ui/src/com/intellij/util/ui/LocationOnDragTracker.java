// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Calculates location of a component being dragged. Correctly handles cross-monitor drag.
 * The approach used is that the new location is calculated as an offset from the mouse pointer,
 * not the delta b/w the old and new mouse pointer positions. This solves the problem of
 * a drag b/w monitors with not continues bounds (when the delta b/w mouse positions can
 * be invalid).
 *
 * @author tav
 */
public final class LocationOnDragTracker {
  private Point myOffsetXY;
  private Rectangle myMonitorBounds;
  private double myScale;

  private LocationOnDragTracker(MouseEvent e) {
    myOffsetXY = e.getPoint();
    myMonitorBounds = e.getComponent().getGraphicsConfiguration().getBounds();
    myScale = JBUIScale.sysScale(e.getComponent());
  }

  /**
   * Creates and returns an instance encapsulating info about a drag start by the provided {@link MouseEvent#MOUSE_PRESSED} event.
   */
  public static LocationOnDragTracker startDrag(@NotNull MouseEvent pressEvent) {
    assert pressEvent.getID() == MouseEvent.MOUSE_PRESSED;
    return new LocationOnDragTracker(pressEvent);
  }

  /**
   * Updates the location of the dragged component on drag progress.
   */
  public void updateLocationOnDrag(@NotNull Component draggedComp) {
    PointerInfo mouseInfo = MouseInfo.getPointerInfo();
    if (mouseInfo == null) return;

    Point mouseLocation = mouseInfo.getLocation();
    Point offsetXY = myOffsetXY.getLocation();

    if (!myMonitorBounds.contains(mouseLocation)) {
      for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
        if (gd.getDefaultConfiguration().getBounds().contains(mouseLocation)) {
          double scale = JBUIScale.sysScale(gd.getDefaultConfiguration());
          int offX = (int)(myOffsetXY.x * myScale / scale);
          int offY = (int)(myOffsetXY.y * myScale / scale);
          myOffsetXY = new Point(offX, offY);
          myMonitorBounds = gd.getDefaultConfiguration().getBounds();
          myScale = scale;
          offsetXY.setLocation(myOffsetXY);
        }
      }
    }
    Point newLocation = mouseLocation.getLocation();
    newLocation.translate(-offsetXY.x, -offsetXY.y);
    draggedComp.setLocation(newLocation);
  }
}