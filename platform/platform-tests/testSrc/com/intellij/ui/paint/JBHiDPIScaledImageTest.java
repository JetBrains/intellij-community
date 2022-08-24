// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.paint;

import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class JBHiDPIScaledImageTest {
  @Test
  public void testPaintImage() {
    BufferedImage testImg = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
    Color testColor1 = Color.RED;
    Color testColor2 = Color.BLUE;
    {
      Graphics2D g = testImg.createGraphics();
      g.setColor(testColor1);
      g.fillRect(0,0, testImg.getWidth(), testImg.getHeight());
      g.setColor(testColor2);
      g.fillRect(testImg.getWidth() - 2, testImg.getHeight() - 2, 2, 2);
    }
    Icon testIcon = new ImageIcon(testImg);
    ScaleContext ctx = ScaleContext.create();
    BufferedImage image;
    if (GraphicsEnvironment.isHeadless()) {
      // for testing purpose
      image = ImageUtil.createImage(ctx, testIcon.getIconWidth(), testIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.ROUND);
    } else {
      if (StartupUiUtil.isJreHiDPI(ctx)) {
        image = new JBHiDPIScaledImage(ctx, testIcon.getIconWidth(), testIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB_PRE,
                                       PaintUtil.RoundingMode.ROUND);
      } else {
        image = GraphicsEnvironment.getLocalGraphicsEnvironment()
          .getDefaultScreenDevice().getDefaultConfiguration()
          .createCompatibleImage(PaintUtil.RoundingMode.ROUND.round(ctx.apply(testIcon.getIconWidth(), DerivedScaleType.DEV_SCALE)),
                                 PaintUtil.RoundingMode.ROUND.round(ctx.apply(testIcon.getIconHeight(), DerivedScaleType.DEV_SCALE)),
                                 Transparency.TRANSLUCENT);
      }
    }

    Graphics2D g = image.createGraphics();
    try {
      testIcon.paintIcon(null, g, 0, 0);
    }
    finally {
      g.dispose();
    }

    Assert.assertEquals(image.getRGB(0, 0), testColor1.getRGB());
    Assert.assertEquals(image.getRGB(image.getWidth() - 1, image.getHeight() - 1), testColor2.getRGB());
  }
}
