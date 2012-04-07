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

import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.StaticDecorator;
import com.intellij.designer.model.RadComponent;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public abstract class FlowStaticDecorator extends StaticDecorator {
  private static final BasicStroke STROKE = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{3, 1}, 0);
  private static final Color LINE_COLOR = new Color(47, 67, 96);

  public FlowStaticDecorator(RadComponent component) {
    super(component);
  }

  @Override
  protected void paint(DecorationLayer layer, Graphics2D g, RadComponent component) {
    Rectangle bounds = component.getBounds(layer);

    g.setColor(LINE_COLOR);
    g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

    g.setStroke(STROKE);

    if (isHorizontal()) {
      for (RadComponent child : component.getChildren()) {
        Rectangle childBounds = child.getBounds(layer);
        g.drawLine(childBounds.x + childBounds.width, bounds.y, childBounds.x + childBounds.width, bounds.y + bounds.height);
      }
    }
    else {
      for (RadComponent child : component.getChildren()) {
        Rectangle childBounds = child.getBounds(layer);
        g.drawLine(bounds.x, childBounds.y + childBounds.height, bounds.x + bounds.width, childBounds.y + childBounds.height);
      }
    }
  }

  protected abstract boolean isHorizontal();
}