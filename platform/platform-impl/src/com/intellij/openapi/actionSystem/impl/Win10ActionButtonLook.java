/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Path2D;

@ApiStatus.Internal
public class Win10ActionButtonLook extends ActionButtonLook {
  @Override
  public void paintLookBackground(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {
    g.setColor(color);
    g.fillRect(rect.x, rect.y, rect.width, rect.height);
  }

  @Override
  public void paintLookBorder(@NotNull Graphics g, @NotNull Rectangle rect, @NotNull Color color) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.setColor(color);

      Rectangle innerRect = new Rectangle(rect);
      JBInsets.removeFrom(innerRect, JBUI.insets(1));

      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(rect, false);
      border.append(innerRect, false);

      g2.fill(border);
    }
    finally {
      g2.dispose();
    }
  }
}
