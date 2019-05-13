// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;

/**
 * @author Eugene Belyaev
 */
final class Surface extends JComponent {
  private final Image myTopImage;
  private final Image myBottomImage;
  private final Point2D myBottomImageOffset;
  private final int myDirection;
  private final int myDesiredTimeToComplete;
  private final ToolWindowAnchor myAnchor;
  private int myOffset = 0;

  Surface(final Image topImage,
                 final Image bottomImage,
                 final Point2D bottomImageOffset,
                 final int direction,
                 final ToolWindowAnchor anchor,
                 final int desiredTimeToComplete) {
    myTopImage = topImage;
    myBottomImage = bottomImage;
    myBottomImageOffset = (Point2D)bottomImageOffset.clone();
    myAnchor = anchor;
    myDirection = direction;
    myDesiredTimeToComplete = desiredTimeToComplete;
    setOpaque(true);
  }

  public final void runMovement() {
    if (!isShowing()) {
      return;
    }
    final int distance;
    final Rectangle bounds = getBounds();
    if (myAnchor == ToolWindowAnchor.LEFT || myAnchor == ToolWindowAnchor.RIGHT) {
      distance = bounds.width;
    }
    else {
      distance = bounds.height;
    }
    int count = 0;
    myOffset = 0;
    paintImmediately(0, 0, getWidth(), getHeight());//first paint requires more time than next ones
    final long startTime = System.currentTimeMillis();


    while (true) {
      paintImmediately(0, 0, getWidth(), getHeight());
      final long timeSpent = System.currentTimeMillis() - startTime;
      count++;
      if (timeSpent >= myDesiredTimeToComplete) break;
      final double onePaintTime = (double)timeSpent / count;
      int iterations = (int)((myDesiredTimeToComplete - timeSpent) / onePaintTime);
      iterations = Math.max(1, iterations);
      myOffset += (distance - myOffset) / iterations;
    }
  }

  @Override
  public final void paint(final Graphics g) {
    final Rectangle bounds = getBounds();
    ((Graphics2D)g).translate(myBottomImageOffset.getX(), myBottomImageOffset.getY());
    if (myAnchor == ToolWindowAnchor.LEFT) {
      if (myDirection == 1) {
        g.setClip(null);
        g.clipRect(myOffset, 0, bounds.width - myOffset, bounds.height);
        UIUtil.drawImage(g, myBottomImage, 0, 0, null);
        g.setClip(null);
        g.clipRect(0, 0, myOffset, bounds.height);
        UIUtil.drawImage(g, myTopImage, myOffset - bounds.width, 0, null);
      }
      else {
        g.setClip(null);
        g.clipRect(bounds.width - myOffset, 0, myOffset, bounds.height);
        UIUtil.drawImage(g, myBottomImage, 0, 0, null);
        g.setClip(null);
        g.clipRect(0, 0, bounds.width - myOffset, bounds.height);
        UIUtil.drawImage(g, myTopImage, -myOffset, 0, null);
      }
      myTopImage.flush();
    }
    else if (myAnchor == ToolWindowAnchor.RIGHT) {
      if (myDirection == 1) {
        g.setClip(null);
        g.clipRect(0, 0, bounds.width - myOffset, bounds.height);
        UIUtil.drawImage(g, myBottomImage, 0, 0, null);
        g.setClip(null);
        g.clipRect(bounds.width - myOffset, 0, myOffset, bounds.height);
        UIUtil.drawImage(g, myTopImage, bounds.width - myOffset, 0, null);
      }
      else {
        g.setClip(null);
        g.clipRect(0, 0, myOffset, bounds.height);
        UIUtil.drawImage(g, myBottomImage, 0, 0, null);
        g.setClip(null);
        g.clipRect(myOffset, 0, bounds.width - myOffset, bounds.height);
        UIUtil.drawImage(g, myTopImage, myOffset, 0, null);
      }
    }
    else if (myAnchor == ToolWindowAnchor.TOP) {
      if (myDirection == 1) {
        g.setClip(null);
        g.clipRect(0, myOffset, bounds.width, bounds.height - myOffset);
        UIUtil.drawImage(g, myBottomImage, 0, 0, null);
        g.setClip(null);
        g.clipRect(0, 0, bounds.width, myOffset);
        UIUtil.drawImage(g, myTopImage, 0, -bounds.height + myOffset, null);
      }
      else {
        g.setClip(null);
        g.clipRect(0, bounds.height - myOffset, bounds.width, myOffset);
        UIUtil.drawImage(g, myBottomImage, 0, 0, null);
        g.setClip(null);
        g.clipRect(0, 0, bounds.width, bounds.height - myOffset);
        UIUtil.drawImage(g, myTopImage, 0, -myOffset, null);
      }
    }
    else if (myAnchor == ToolWindowAnchor.BOTTOM) {
      if (myDirection == 1) {
        g.setClip(null);
        g.clipRect(0, 0, bounds.width, bounds.height - myOffset);
        UIUtil.drawImage(g, myBottomImage, 0, 0, null);
        g.setClip(null);
        g.clipRect(0, bounds.height - myOffset, bounds.width, myOffset);
        UIUtil.drawImage(g, myTopImage, 0, bounds.height - myOffset, null);
      }
      else {
        g.setClip(null);
        g.clipRect(0, 0, bounds.width, myOffset);
        UIUtil.drawImage(g, myBottomImage, 0, 0, null);
        g.setClip(null);
        g.clipRect(0, myOffset, bounds.width, bounds.height - myOffset);
        UIUtil.drawImage(g, myTopImage, 0, myOffset, null);
      }
    }
  }
}
