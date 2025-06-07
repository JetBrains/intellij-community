// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;

public final class MixedColorProducerTest {
  @Test
  public void checkFirstColorInstance() {
    Assert.assertSame(Color.BLACK, getBlackWhite(0));
    Assert.assertSame(Color.WHITE, getWhiteBlack(0));
  }

  @Test
  public void checkSecondColorInstance() {
    Assert.assertSame(Color.WHITE, getBlackWhite(1));
    Assert.assertSame(Color.BLACK, getWhiteBlack(1));
  }

  @Test
  public void checkCachedColorInstance() {
    MixedColorProducer producer = new MixedColorProducer(new Color(0xFF, 0, 0, 0), Color.RED);
    Color color1 = producer.produce(.999);
    Color color2 = producer.produce(.999);
    Assert.assertEquals(color1, color2);
    Assert.assertSame(color1, color2);
    Color color3 = producer.produce(.9999);
    Assert.assertEquals(color1, color3);
    Assert.assertNotSame(color1, color3);
  }


  private static void testInvalidValue(double mixer) {
    try {
      getTransparentRed(mixer);
      Assert.fail("invalid value: " + mixer);
    }
    catch (IllegalArgumentException ignore) {
    }
  }

  @Test
  public void testMinNegativeValue() {
    testInvalidValue(-Double.MIN_VALUE);
  }

  @Test
  public void testMaxNegativeValue() {
    testInvalidValue(-Double.MAX_VALUE);
  }

  @Test
  public void testMaxPositiveValue() {
    testInvalidValue(Double.MAX_VALUE);
  }

  @Test
  public void testNegativeInfinity() {
    testInvalidValue(Double.NEGATIVE_INFINITY);
  }

  @Test
  public void testPositiveInfinity() {
    testInvalidValue(Double.POSITIVE_INFINITY);
  }

  @Test
  public void testNaN() {
    testInvalidValue(Double.NaN);
  }


  @NotNull
  private static Color getBlackWhite(double mixer) {
    return new MixedColorProducer(Color.BLACK, Color.WHITE).produce(mixer);
  }

  @Test
  public void testBlackWhite25() {
    assertColor(getBlackWhite(.25), 0x404040);
  }

  @Test
  public void testBlackWhite50() {
    assertColor(getBlackWhite(.50), 0x808080);
  }

  @Test
  public void testBlackWhite75() {
    assertColor(getBlackWhite(.75), 0xBFBFBF);
  }

  @Test
  public void testBlackWhiteAll() {
    MixedColorProducer producer = new MixedColorProducer(Color.BLACK, Color.WHITE);
    for (int i = 0; i <= 0xFF; i++) {
      Color color = producer.produce((float)i / 0xFF);
      assertGrayColor(color, i);
    }
  }

  @NotNull
  private static Color getWhiteBlack(double mixer) {
    return new MixedColorProducer(Color.WHITE, Color.BLACK).produce(mixer);
  }

  @Test
  public void testWhiteBlack25() {
    assertColor(getWhiteBlack(.25), 0xBFBFBF);
  }

  @Test
  public void testWhiteBlack50() {
    assertColor(getWhiteBlack(.50), 0x808080);
  }

  @Test
  public void testWhiteBlack75() {
    assertColor(getWhiteBlack(.75), 0x404040);
  }

  @Test
  public void testWhiteBlackAll() {
    MixedColorProducer producer = new MixedColorProducer(Color.WHITE, Color.BLACK);
    for (int i = 0; i <= 0xFF; i++) {
      Color color = producer.produce((float)i / 0xFF);
      assertGrayColor(color, 0xFF - i);
    }
  }


  @NotNull
  private static Color getTransparentRed(double mixer) {
    return new MixedColorProducer(new Color(0xFF, 0, 0, 0), Color.RED).produce(mixer);
  }

  @Test
  public void testTransparentRed25() {
    assertColorWithAlpha(getTransparentRed(.25), 0x40FF0000);
  }

  @Test
  public void testTransparentRed50() {
    assertColorWithAlpha(getTransparentRed(.50), 0x80FF0000);
  }

  @Test
  public void testTransparentRed75() {
    assertColorWithAlpha(getTransparentRed(.75), 0xBFFF0000);
  }


  private static void assertColor(@NotNull Color actual, int expected) {
    assertColor(actual, new Color(expected, false));
  }

  private static void assertColorWithAlpha(@NotNull Color actual, int expected) {
    assertColor(actual, new Color(expected, true));
  }

  private static void assertGrayColor(@NotNull Color actual, int expected) {
    assertColor(actual, new Color(expected, expected, expected));
  }

  private static void assertColor(@NotNull Color actual, @NotNull Color expected) {
    Assert.assertEquals(expected, actual);
  }
}
