/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

public class BegScrollBarUI extends BasicScrollBarUI {
  public static ComponentUI createUI(JComponent c) {
    return new BegScrollBarUI();
  }

  protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
    if (thumbBounds.isEmpty() || !scrollbar.isEnabled()){
      return;
    }

    int w = thumbBounds.width;
    int h = thumbBounds.height;

    g.translate(thumbBounds.x, thumbBounds.y);

    g.setColor(thumbDarkShadowColor);
//    g.drawRect(0, 0, w - 1, h - 1);
    UIUtil.drawLine(g, 0, 1, 0, h - 2);
    //left
    UIUtil.drawLine(g, 1, 0, w - 2, 0);
    //top
    UIUtil.drawLine(g, w - 1, 1, w - 1, h - 2);
    //right
    UIUtil.drawLine(g, 1, h - 1, w - 2, h - 1);
    //bottom
//    g.setColor(thumbColor);
    g.setColor(new Color(247, 243, 239));
    g.fillRect(1, 1, w - 2, h - 2);

//    g.setColor(thumbHighlightColor);
//    g.setColor(Color.darkGray);
//    g.drawLine(1, 1, 1, h - 2);
//    g.drawLine(2, 1, w - 3, 1);

//    g.setColor(thumbLightShadowColor);
//    g.drawLine(2, h - 2, w - 2, h - 2);
//    g.drawLine(w - 2, 1, w - 2, h - 3);

    g.translate(-thumbBounds.x, -thumbBounds.y);
  }

  protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
    g.setColor(trackColor);
    g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);

    if (trackHighlight == DECREASE_HIGHLIGHT){
      paintDecreaseHighlight(g);
    }
    else
      if (trackHighlight == INCREASE_HIGHLIGHT){
        paintIncreaseHighlight(g);
      }
  }
}