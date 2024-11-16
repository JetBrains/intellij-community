package org.jetbrains.intellij.plugins.journey.diagram;

import java.awt.*;
import javax.swing.*;

public class JourneyTitleBar extends JComponent {
  private String title;

  public JourneyTitleBar(String title) {
    this.title = title;
    setPreferredSize(new Dimension(400, 20));
    setBackground(Color.DARK_GRAY);
    setForeground(Color.WHITE);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D) g;
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    // Draw background
    g2d.setColor(getBackground());
    g2d.fillRect(0, 0, getWidth(), getHeight());

    // Draw title text
    g2d.setColor(getForeground());
    FontMetrics fm = g2d.getFontMetrics();
    int x = 10;
    int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
    g2d.drawString(title, x, y);
  }
}