package org.jetbrains.plugins.gradle.util;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 11/22/12 7:52 PM
 */
public class TextIcon implements Icon {

  @NotNull private final String myText;

  private final int myWidth;
  private final int myHeight;

  public TextIcon(@NotNull String text) {
    myText = text;
    JLabel label = new JLabel("");
    Font font = label.getFont();
    FontMetrics metrics = label.getFontMetrics(font);
    myWidth = metrics.stringWidth(text) + 4;
    myHeight = font.getSize();
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    g.setColor(UIUtil.getLabelForeground());
    g.drawString(myText, x + 2, y + myHeight); 
  }

  @Override
  public int getIconWidth() {
    return myWidth;
  }

  @Override
  public int getIconHeight() {
    return myHeight;
  }
}
