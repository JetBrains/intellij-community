// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class Gradient {
  private final Color myStartColor;
  private final Color myEndColor;

  public Gradient(Color startColor, Color endColor) {
    myStartColor = startColor;
    myEndColor = endColor;
  }

  public Color getStartColor() {
    return myStartColor;
  }

  public Color getEndColor() {
    return myEndColor;
  }
}
