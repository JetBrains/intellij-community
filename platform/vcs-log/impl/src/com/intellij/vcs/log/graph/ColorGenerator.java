package com.intellij.vcs.log.graph;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;

/**
 * @author erokhins
 */
public class ColorGenerator {

  private static final Map<Integer, Color> ourColorMap = ContainerUtil.newHashMap();

  @NotNull
  public static Color getColor(int branchNumber) {
    Color color = ourColorMap.get(branchNumber);
    if (color == null) {
      color = calcColor(branchNumber);
      ourColorMap.put(branchNumber, color);
    }
    return color;
  }

  private static int rangeFix(int n) {
    return Math.abs(n) % 100 + 70;
  }

  @NotNull
  private static Color calcColor(int indexColor) {
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
