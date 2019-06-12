// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
