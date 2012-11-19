/*
 * @author max
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import javax.swing.border.EmptyBorder;
import java.awt.*;

public class BottomLineBorder extends EmptyBorder {
  public BottomLineBorder() {
    super(0, 0, 1, 0);
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    g.setColor(WelcomeScreenColors.BORDER_COLOR);
    g.drawLine(x, y + height - 1, x + width, y + height - 1);
  }
}
