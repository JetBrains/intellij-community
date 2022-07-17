// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.ui.ComponentUtil;
import com.intellij.ui.awt.DevicePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
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
  private final Point myInitialOffset;

  private LocationOnDragTracker(MouseEvent e) {
    e = SwingUtilities.convertMouseEvent(e.getComponent(), e, ComponentUtil.getWindow(e.getComponent()));
    myInitialOffset = e.getPoint();
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
  public void updateLocationOnDrag(@NotNull Component draggedComp, @NotNull Point mouseLocation) {
    // Get the mouse location on the correct screen, in the correct screen coordinates
    Point screenMouseLocation = new DevicePoint(mouseLocation, draggedComp).getLocationOnScreen();
    Dimension originalSize = draggedComp.getSize();
    Point newLocation = new Point(screenMouseLocation.x - myInitialOffset.x, screenMouseLocation.y - myInitialOffset.y);
    draggedComp.setLocation(newLocation);

    // Windows can resize the window when it crosses the boundary between two screens with different DPI. It tries to keep the same physical
    // size, based on DPI, so moving from a large DPI scale factor to a lower factor (e.g. 150% to 100%) will cause the window to shrink. By
    // shrinking, it's now not on the boundary anymore, but Windows doesn't update the scale. Continuing to drag will cross the boundary
    // again, and it will shrink again. This repeats until the window vanishes. Going in the opposite direction can cause the window to grow
    // huge. We don't scale the UI, so keep it to the same original size
    // Note that there looks like a bug in the JBR that causes the window to jump to a weirdly large x value, as though it's being scaled
    // multiple times. See the note in ToolWindowDragHelper.relocate for more
    if (draggedComp.getSize() != originalSize) {
      draggedComp.setSize(originalSize);
    }
  }
}