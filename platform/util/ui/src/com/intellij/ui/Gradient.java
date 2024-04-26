// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public final class Gradient {
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
