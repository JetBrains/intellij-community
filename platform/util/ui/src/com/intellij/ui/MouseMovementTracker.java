// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Arrays;

/**
 * This class keeps a history of mouse location changes, and is able to tell whether mouse is currently moving towards specified rectangular
 * region on screen. Due to 'rasterization' of mouse locations (mapping to a grid of finite-size pixels), keeping only the previous mouse
 * mouse location is not enough - a diagonal mouse movement can contain purely horizontal or vertical 'steps'.
 */
public final class MouseMovementTracker {
  private static final int HISTORY_SIZE = 2;
  private static final int MOVEMENT_MARGIN_PX = 2;

  private final Point[] myHistory = new Point[HISTORY_SIZE];
  private int myCurrentIndex;

  public void reset() {
    Arrays.fill(myHistory, null);
  }

  public boolean isMovingTowards(@NotNull MouseEvent me, @Nullable Rectangle rectangleOnScreen) {
    Point currentLocation = me.getLocationOnScreen();
    // finding previous location
    Point previousLocation = null;
    for (int i = 0; i < HISTORY_SIZE; i++) {
      Point p = myHistory[(myCurrentIndex - i + HISTORY_SIZE) % HISTORY_SIZE];
      if (p != null && p.distance(currentLocation) >= MOVEMENT_MARGIN_PX) {
        previousLocation = p;
        break;
      }
    }
    // storing current location
    myCurrentIndex = (myCurrentIndex + 1) % HISTORY_SIZE;
    myHistory[myCurrentIndex] = currentLocation;

    return ScreenUtil.isMovementTowards(previousLocation, currentLocation, rectangleOnScreen);
  }
}
