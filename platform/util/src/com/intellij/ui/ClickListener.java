/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public abstract class ClickListener {

  private static final int EPS = 4;
  private MouseAdapter myListener;

  public abstract boolean onClick(@NotNull MouseEvent event, int clickCount);

  public void installOn(@NotNull Component c) {
    myListener = new MouseAdapter() {
      private Point pressPoint;
      private Point lastClickPoint;
      private long lastTimeClicked = -1;
      private int clickCount = 0;

      @Override
      public void mousePressed(MouseEvent e) {
        final Point point = e.getPoint();
        SwingUtilities.convertPointToScreen(point, e.getComponent());

        if (Math.abs(lastTimeClicked - e.getWhen()) > UIUtil.getMultiClickInterval() || lastClickPoint != null && !isWithinEps(lastClickPoint, point)) {
          clickCount = 0;
          lastClickPoint = null;
        }
        clickCount++;
        lastTimeClicked = e.getWhen();

        if (!e.isPopupTrigger()) {
          pressPoint = point;
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        Point releasedAt = e.getPoint();
        SwingUtilities.convertPointToScreen(releasedAt, e.getComponent());
        Point clickedAt = pressPoint;
        lastClickPoint = clickedAt;
        pressPoint = null;

        if (e.isConsumed() || clickedAt == null || e.isPopupTrigger() || !e.getComponent().contains(e.getPoint())) {
          return;
        }

        if (isWithinEps(releasedAt, clickedAt) && onClick(e, clickCount)) {
          e.consume();
        }
      }
    };

    c.addMouseListener(myListener);
  }

  private static boolean isWithinEps(Point releasedAt, Point clickedAt) {
    return Math.abs(clickedAt.x - releasedAt.x) < EPS && Math.abs(clickedAt.y - releasedAt.y) < EPS;
  }

  public void uninstall(Component c) {
    c.removeMouseListener(myListener);
  }
}
