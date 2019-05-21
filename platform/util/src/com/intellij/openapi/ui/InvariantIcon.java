package com.intellij.openapi.ui;

import com.intellij.openapi.util.Condition;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class InvariantIcon implements Icon {
  public InvariantIcon(Icon base, Icon optional, Condition condition) {
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
  }

  @Override
  public int getIconWidth() {
    return 0;
  }

  @Override
  public int getIconHeight() {
    return 0;
  }
}
