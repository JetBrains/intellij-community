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

import com.intellij.android.designer.model.agrid.GridInfo;
import com.intellij.android.designer.model.agrid.IGridProvider;
import com.intellij.designer.designSurface.DecorationLayer;
import com.intellij.designer.designSurface.StaticDecorator;
import com.intellij.designer.model.RadComponent;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class GridDecorator extends StaticDecorator {
  public GridDecorator(RadComponent component) {
    super(component);
  }

  @Override
  protected void paint(DecorationLayer layer, Graphics2D g, RadComponent component) {
    Stroke stroke = g.getStroke();
    g.setColor(BorderStaticDecorator.COLOR);
    g.setStroke(FlowStaticDecorator.STROKE);

    Rectangle bounds = component.getBounds(layer);
    GridInfo gridInfo = ((IGridProvider)component).getGridInfo();

    for (int x : gridInfo.vLines) {
      g.drawLine(bounds.x + x, bounds.y, bounds.x + x, bounds.y + gridInfo.height);
    }
    for (int y : gridInfo.hLines) {
      g.drawLine(bounds.x, bounds.y + y, bounds.x + gridInfo.width, bounds.y + y);
    }

    g.setStroke(stroke);
    g.drawRect(bounds.x, bounds.y, gridInfo.width, gridInfo.height);
    g.drawRect(bounds.x + 1, bounds.y + 1, gridInfo.width - 2, gridInfo.height - 2);
  }
}