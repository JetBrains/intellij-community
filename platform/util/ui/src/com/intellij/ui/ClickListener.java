// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    installOn(c, false);
  }

  public void installOn(@NotNull Component c, boolean allowDragWhileClicking) {
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

        if ((allowDragWhileClicking || isWithinEps(releasedAt, clickedAt)) && onClick(e, clickCount)) {
          e.consume();
        }
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (e instanceof SyntheticClickEvent && !e.isConsumed() && onClick(e, e.getClickCount())) {
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

  public static final class SyntheticClickEvent extends MouseEvent {
    public SyntheticClickEvent(Component source,
                               long when, int modifiers,
                               int x, int y, int clickCount, boolean popupTrigger, int button) {
      super(source, MouseEvent.MOUSE_CLICKED, when, modifiers, x, y, clickCount, popupTrigger, button);
    }
  }
}
