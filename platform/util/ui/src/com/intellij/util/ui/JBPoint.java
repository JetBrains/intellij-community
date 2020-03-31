// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class JBPoint extends Point {
  public JBPoint(@NotNull Point p) {
    super(p instanceof JBPoint ? p : new JBPoint(p.x, p.y));
  }

  public JBPoint(int x, int y) {
    super(JBUIScale.scale(x), JBUIScale.scale(y));
  }
}
