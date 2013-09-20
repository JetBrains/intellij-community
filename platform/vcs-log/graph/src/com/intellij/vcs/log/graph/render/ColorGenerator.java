package com.intellij.vcs.log.graph.render;

import com.intellij.vcs.log.graph.elements.Branch;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author erokhins
 */
public class ColorGenerator {
  private static final Map<Integer, Color> colorMap = new HashMap<Integer, Color>();

  private static final Map<Branch, Color> ourCachedColors = new HashMap<Branch, Color>();

  @NotNull
  public static Color getColor(@NotNull Branch branch) {
    int indexColor = branch.getBranchNumber();
    Color color = colorMap.get(indexColor);
    if (color == null) {
      color = getColor(indexColor);
      colorMap.put(indexColor, color);
    }
    return color;
  }

  private static int rangeFix(int n) {
    return Math.abs(n) % 200;
  }


  private static Color getColor(int indexColor) {
    int r = indexColor * 200 + 30;
    int g = indexColor * 130 + 50;
    int b = indexColor * 90 + 100;
    try {
      return new Color(rangeFix(r), rangeFix(g), rangeFix(b));
    }
    catch (IllegalArgumentException a) {
      throw new IllegalArgumentException("indexColor: " + indexColor + " " + r % 256 + " " + (g % 256) + " " + (b % 256));
    }
  }
}
