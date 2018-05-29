// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.LafManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.RGBImageFilter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class ToolWindowIcon implements Icon {
  private static final Map<Icon, int[]> ourCache = new HashMap<>();
  static {
    LafManager.getInstance().addLafManagerListener(x -> ourCache.clear());
  }

  private final Icon myIcon;
  private final boolean myUseOriginal;

  public ToolWindowIcon(Icon icon, String toolWindowId) {
    myIcon = icon;
    if (Arrays.asList("Event Log", "Problems").contains(toolWindowId)) {
      myUseOriginal = true;
    } else {
      int[] rgb = ColorThief.getColor(ImageUtil.toBufferedImage(IconUtil.toImage(icon)));
      @SuppressWarnings("UseJBColor")
      Color color = rgb != null && rgb.length == 3 ? new Color(rgb[0], rgb[1], rgb[2]) : null;
      myUseOriginal = color == null || color.equals(Gray._108) || color.equals(ColorUtil.fromHex("AFB1B3"));
    }
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    if (!Registry.is("ide.convert.tool.window.icons") || myUseOriginal) {
      myIcon.paintIcon(c, g, x, y);
      return;
    }

    int[] dominant = getDominantColor(myIcon);
    if (dominant == null || dominant.length != 3) {
      myIcon.paintIcon(c, g, x, y);
      return;
    }

    double brightnessDominant = 0.299 * dominant[0] + 0.587 * dominant[1] + 0.114 * dominant[2];
    double k = brightnessDominant / getBaseGray();
    RGBImageFilter filter = new RGBImageFilter() {
      @Override
      public int filterRGB(int x, int y, int rgb) {
        int a = (rgb >> 24) & 0xff;
        double brightness = 0.299 * ((rgb >> 16) & 0xff) + 0.587 * ((rgb >> 8) & 0xff) + 0.114 * (rgb & 0xff);
        double d = k == 0 ? 255f : brightness / k;
        try {
          return (       (a & 0xFF) << 24)
                 | (((int)d & 0xFF) << 16)
                 | (((int)d & 0xFF) << 8)
                 | (((int)d & 0xFF));
        } catch (Exception e) {
          return 0;
        }
      }
    };
    ScaleContext ctx = ScaleContext.create(c, (Graphics2D)g);
    Image rawImage = ImageUtil.filter(IconUtil.toImage(myIcon, ctx), filter);
    Image hidpiImage = ImageUtil.ensureHiDPI(rawImage, ctx);
    UIUtil.drawImage(g, hidpiImage, x, y, null);
  }

  private static int getBaseGray() {
    return UIUtil.isUnderDarcula() ? 0xB1 : 0x6C;
  }

  private static int[] getDominantColor(Icon icon) {
    int[] rgb = ourCache.get(icon);
    if (rgb == null) {
      ourCache.put(icon, rgb = ColorThief.getColor(ImageUtil.toBufferedImage(IconUtil.toImage(icon))));
    }
    return rgb;
  }

  @Override
  public int getIconWidth() {
    return myIcon.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return myIcon.getIconHeight();
  }
}
