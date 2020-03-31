// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class IndentedIcon implements Icon {
  private final Icon myBaseIcon;
  private final int myIndent;

  public IndentedIcon(final Icon baseIcon, final int indent) {
    myBaseIcon = baseIcon;
    myIndent = indent;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    myBaseIcon.paintIcon(c, g, x + myIndent, y);
  }

  @Override
  public int getIconWidth() {
    return myIndent + myBaseIcon.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return myBaseIcon.getIconHeight();
  }
}
