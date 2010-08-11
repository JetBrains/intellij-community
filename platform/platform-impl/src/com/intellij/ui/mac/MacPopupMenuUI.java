/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.mac;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * User: spLeaner
 */
public class MacPopupMenuUI extends BasicPopupMenuUI {
  static Stroke THREE_PIXEL_STROKE = new BasicStroke(3F);

  public MacPopupMenuUI() {
  }

  public static ComponentUI createUI(final JComponent c) {
    return new MacPopupMenuUI();
  }

  public boolean isPopupTrigger(final MouseEvent event) {
    return event.isPopupTrigger();
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
  }

  public void paint(final Graphics g, final JComponent jcomponent) {
    if (!(g instanceof Graphics2D)) {
      super.paint(g, jcomponent);
      return;
    }

    Graphics2D graphics2d = (Graphics2D)g.create();
    Rectangle rectangle = popupMenu.getBounds();
    paintRoundRect(graphics2d, rectangle);
    clipEdges(graphics2d, rectangle);
    graphics2d.dispose();

    super.paint(g, jcomponent);
  }

  private static void paintRoundRect(Graphics2D graphics2d, Rectangle rectangle) {
    graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics2d.setComposite(AlphaComposite.Clear);
    graphics2d.setStroke(THREE_PIXEL_STROKE);
    graphics2d.drawRoundRect(-2, -2, rectangle.width + 3, rectangle.height + 3, 12, 12);
  }

  protected void clipEdges(Graphics2D graphics2d, Rectangle rectangle) {
    Component component = popupMenu.getInvoker();
    if (!(component instanceof JMenu)) return;

    Rectangle rectangle1 = component.getBounds();

    rectangle1.setLocation(component.getLocationOnScreen());
    rectangle.setLocation(popupMenu.getLocationOnScreen());

    Point point = new Point((int)rectangle1.getCenterX(), (int)rectangle1.getCenterY());
    if (rectangle.contains(point)) return;

    graphics2d.setComposite(AlphaComposite.SrcOver);
    graphics2d.setColor(popupMenu.getBackground());

    Point point1 = new Point((int)rectangle.getCenterX(), (int)rectangle.getCenterY());
    boolean flag = point.y <= point1.y;

    if (rectangle1.x + rectangle1.width < rectangle.x + 10) {
      if (flag) {
        graphics2d.fillRect(-2, -2, 8, 8);
        return;
      }
      else {
        graphics2d.fillRect(-2, rectangle.height - 6, 8, 8);
        return;
      }
    }

    if (rectangle.x + rectangle.width < rectangle1.x + 10) {
      if (flag) {
        graphics2d.fillRect(rectangle.width - 6, -2, 8, 8);
        return;
      }
      else {
        graphics2d.fillRect(rectangle.width - 6, rectangle.height - 6, 8, 8);
        return;
      }
    }

    if (rectangle1.y + rectangle1.height < rectangle.y + 10) {
      graphics2d.fillRect(-2, -2, rectangle.width + 4, 8);
    }
  }
}
