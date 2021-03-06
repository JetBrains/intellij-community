// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.paint;

import com.intellij.ui.RestoreScaleRule;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ui.ImageUtil;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import static com.intellij.ui.scale.TestScaleHelper.overrideJreHiDPIEnabled;

public final class EffectPainterTest {
  @ClassRule
  public static final ExternalResource manageState = new RestoreScaleRule();

  public static final int[] SIZES = {2, 4, 8, 16, 32};
  private static final Color BACKGROUND = Color.WHITE;
  private static final Color FOREGROUND = Color.BLACK;

  @Test
  public void testLineUnderscore() {
    overrideJreHiDPIEnabled(false);
    testPainter(EffectPainter2D.LINE_UNDERSCORE);

    overrideJreHiDPIEnabled(true);
    testPainter(EffectPainter2D.LINE_UNDERSCORE);
  }

  @Test
  public void testBoldLineUnderscore() {
    overrideJreHiDPIEnabled(false);
    testPainter(EffectPainter2D.BOLD_LINE_UNDERSCORE);

    overrideJreHiDPIEnabled(true);
    testPainter(EffectPainter2D.BOLD_LINE_UNDERSCORE);
  }

  @Test
  public void testBoldDottedUnderscore() {
    overrideJreHiDPIEnabled(false);
    testPainter(EffectPainter2D.BOLD_DOTTED_UNDERSCORE);

    overrideJreHiDPIEnabled(true);
    testPainter(EffectPainter2D.BOLD_DOTTED_UNDERSCORE);
  }

  @Test
  public void testWaveUnderscore() {
    overrideJreHiDPIEnabled(false);
    testPainter(EffectPainter2D.WAVE_UNDERSCORE);

    overrideJreHiDPIEnabled(true);
    testPainter(EffectPainter2D.WAVE_UNDERSCORE);
  }

  @Test
  public void testStrikeThrough() {
    overrideJreHiDPIEnabled(false);
    testPainter(EffectPainter2D.STRIKE_THROUGH);

    overrideJreHiDPIEnabled(true);
    testPainter(EffectPainter2D.STRIKE_THROUGH);
  }

  private static void testPainter(EffectPainter2D painter) {
    for (int size : SIZES) testPainter(painter, 100, size);
  }

  private static void testPainter(EffectPainter2D painter, int width, int height) {
    if (painter == EffectPainter2D.STRIKE_THROUGH) height += 10; // default font size
    testPainter(painter, ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB));
  }

  private static void testPainter(EffectPainter2D painter, BufferedImage image) {
    String[] content = createContent(image, painter, 0);
    testContent(content, content.length / 2, false);

    for (int size : SIZES) testPartialContent(painter, image, content, size);
    testPartialContent(painter, image, content, content.length / 2);

    Assert.assertEquals("unexpected amount of empty columns", painter == EffectPainter2D.BOLD_DOTTED_UNDERSCORE, hasEmpty(content));
    Assert.assertTrue("image is not clear if offset too large", isEmpty(createContent(image, painter, content.length)));
    Assert.assertTrue("image is not clear if nothing to paint", isEmpty(createContent(image, null, 0)));
  }

  private static void testPartialContent(EffectPainter2D painter, BufferedImage image, String[] content, int offset) {
    String[] partial = createContent(image, painter, offset);
    int length = content.length;
    Assert.assertEquals("content size differs", length, partial.length);
    for (int i = offset; i < length; i++) {
      Assert.assertEquals("content differs", content[i], partial[i]);
    }
    boolean out = painter == EffectPainter2D.BOLD_DOTTED_UNDERSCORE; // may be painted out of area
    testContent(partial, offset, out ? null : true);
  }

  private static String[] createContent(BufferedImage image, EffectPainter2D painter, int offset) {
    return createContent(image, painter, FOREGROUND, offset);
  }

  private static String[] createContent(BufferedImage image, EffectPainter2D painter, Paint foreground, int offset) {
    int width = image.getWidth();
    int height = image.getHeight();
    double width2D = width;
    double height2D = height;
    double offset2D = offset;
    // prepare buffers
    String[] content = new String[width];
    char[] chars = new char[height];
    if (image instanceof JBHiDPIScaledImage) {
      double scale = ((JBHiDPIScaledImage)image).getScale();
      width2D = width / scale;
      height2D = height / scale;
      offset2D = offset / scale;
    }
    Graphics2D graphics = image.createGraphics();
    // clear image content
    graphics.setPaint(BACKGROUND);
    RectanglePainter2D.FILL.paint(graphics, 0, 0, width2D, height2D);
    if (painter != null) {
      // paint image content
      boolean over = painter == EffectPainter2D.STRIKE_THROUGH; // paint over baseline
      graphics.setPaint(foreground);
      painter.paint(graphics, offset2D, over ? height2D : 0, width2D - offset2D, height2D, null);
    }
    graphics.dispose();
    // convert content
    int bg = BACKGROUND.getRGB();
    int fg = FOREGROUND.getRGB();
    for (int x = 0; x < content.length; x++) {
      for (int y = 0; y < chars.length; y++) {
        int rgb = image.getRGB(x, y);
        chars[y] = rgb == bg ? ' ' : rgb == fg ? '0' : '.';
      }
      content[x] = new String(chars);
    }
    return content;
  }

  private static void testContent(String[] content, int offset, Boolean expected) {
    Assert.assertFalse("unexpected right part (empty background)", isEmpty(content, offset));
    if (expected != null) Assert.assertEquals("unexpected left part", expected, isEmpty(content, 0, offset));
  }

  private static boolean isEmpty(String value) {
    return value.chars().allMatch(ch -> ch == ' ');
  }

  private static boolean hasEmpty(String[] content) {
    return Arrays.stream(content).anyMatch(EffectPainterTest::isEmpty);
  }

  private static boolean isEmpty(String[] content) {
    return Arrays.stream(content).allMatch(EffectPainterTest::isEmpty);
  }

  private static boolean isEmpty(String[] content, int offset) {
    return Arrays.stream(content, offset, content.length).allMatch(EffectPainterTest::isEmpty);
  }

  private static boolean isEmpty(String[] content, int offset, int length) {
    return Arrays.stream(content, offset, offset + length).allMatch(EffectPainterTest::isEmpty);
  }
}
