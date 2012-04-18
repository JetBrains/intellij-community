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
package com.intellij.android.designer.designSurface.layout;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.table.RadTableLayoutComponent;
import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.StaticDecorator;
import com.intellij.designer.model.RadComponent;

import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class TableLayoutDecorator extends StaticDecorator {
  public TableLayoutDecorator(RadComponent component) {
    super(component);
  }

  @Override
  protected void paint(DecorationLayer layer, Graphics2D g, RadComponent component) {
    Stroke stroke = g.getStroke();
    g.setColor(BorderStaticDecorator.COLOR);
    g.setStroke(FlowStaticDecorator.STROKE);

    Rectangle bounds = component.getBounds(layer);
    int[] columnWidths = ((RadTableLayoutComponent)component).getColumnWidths();

    if (columnWidths != null && columnWidths.length > 0) {
      int x = bounds.x;

      for (int i = 0; i < columnWidths.length; i++) {
        int width = columnWidths[i];

        if (width > 0) {
          x += width;

          if (i != columnWidths.length - 1 || x < bounds.getMinX()) {
            g.drawLine(x, bounds.y, x, bounds.y + bounds.height);
          }
        }
        else {
          g.drawLine(x + 2, bounds.y, x + 2, bounds.y + bounds.height);
        }
      }

      bounds.width = Math.max(bounds.width, x - bounds.x);
    }

    List<RadComponent> children = component.getChildren();
    RadComponent last = children.isEmpty() ? null : children.get(children.size() - 1);

    for (RadComponent child : children) {
      Rectangle childBounds = child.getBounds(layer);
      int y = childBounds.y + childBounds.height + ((RadViewComponent)child).getMargins().height;

      if (child != last || y < bounds.getMaxY()) {
        g.drawLine(bounds.x, y, bounds.x + bounds.width, y);
      }
    }

    g.setStroke(stroke);
    g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
  }
}