// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import org.jetbrains.annotations.ApiStatus;

import java.awt.*;

@ApiStatus.Internal
public final class TestUtils {
  public static Color getColorAt(Component cefComponent, int x, int y) {
    if (cefComponent instanceof JBCefOsrComponent) {
      return ((JBCefOsrComponent)cefComponent).getColorAt(x, y);
    }

    return null;
  }

  public static Double getPixelDensity(Component cefComponent) {
    if (cefComponent instanceof JBCefOsrComponent) {
      return ((JBCefOsrComponent)cefComponent).getPixelDensity();
    }

    return null;
  }
}
