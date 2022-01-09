// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph;

import com.intellij.ui.JBColor;
import com.intellij.vcs.log.paint.ColorGenerator;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author erokhins
 */
public class DefaultColorGenerator implements ColorGenerator {

  private static final Map<Integer, JBColor> ourColorMap = new HashMap<>();

  static {
    ourColorMap.put(GraphColorManagerImpl.DEFAULT_COLOR, JBColor.BLACK);
  }

  @NotNull
  @Override
  public JBColor getColor(int branchNumber) {
    JBColor color = ourColorMap.get(branchNumber);
    if (color == null) {
      color = calcColor(branchNumber);
      ourColorMap.put(branchNumber, color);
    }
    return color;
  }

  private static int rangeFix(int n) {
    return Math.abs(n % 100) + 70;
  }

  @NotNull
  private static JBColor calcColor(int indexColor) {
    int r = indexColor * 200 + 30;
    int g = indexColor * 130 + 50;
    int b = indexColor * 90 + 100;
    try {
      Color color = new Color(rangeFix(r), rangeFix(g), rangeFix(b));
      return new JBColor(color, color);
    }
    catch (IllegalArgumentException a) {
      throw new IllegalArgumentException("indexColor: " + indexColor + " " + r % 256 + " " + (g % 256) + " " + (b % 256));
    }
  }
}
