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
package git4idea.history.browser;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;

public class CaptionIcon implements Icon {
  private final Color myBgrnd;
  private final Font myFont;
  private final String myText;

  private final int myHeight;
  private final int myWidth;

  public CaptionIcon(Color bgrnd, Font font, String text, final Component someComponent) {
    myBgrnd = bgrnd;
    myFont = font;
    myText = text;

    final FontMetrics fm = someComponent.getFontMetrics(myFont);
    final Rectangle2D bounds = fm.getStringBounds(text, someComponent.getGraphics());
    final double height = (text.equals(text.toLowerCase())) ? (bounds.getHeight() - fm.getMaxDescent()) : bounds.getHeight();
    myHeight = (int) height + 4;
    myWidth = (int) bounds.getWidth() + 4;
  }

  public int getIconHeight() {
    return myHeight;
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    final Graphics2D graphics2D = (Graphics2D)g;
    final Paint oldPaint = graphics2D.getPaint();
    final Stroke oldStroke = graphics2D.getStroke();
    
    graphics2D.setPaint(myBgrnd);
    g.fillRoundRect(x + 2, y, myWidth, myHeight, 2, 2);

    graphics2D.setPaint(UIUtil.getTextAreaForeground());
    final BasicStroke stroke = new BasicStroke(1);
    graphics2D.setStroke(stroke);
    g.drawRoundRect(x + 2, y, myWidth, myHeight - 1, 2, 2);

    g.setColor(UIUtil.getTextAreaForeground());
    g.drawString(myText, x + 4, y + myHeight - 3);  // -2

    graphics2D.setPaint(oldPaint);
    graphics2D.setStroke(oldStroke);
  }

  public int getIconWidth() {
    return myWidth + 4;
  }
}
