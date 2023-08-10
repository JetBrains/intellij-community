// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;

public final class MixedColorProducerTest {
  @Test
  public void checkFirstColorInstance() {
    Assert.assertSame(Color.BLACK, getBlackWhite(0).get());
    Assert.assertSame(Color.WHITE, getWhiteBlack(0).get());
  }

  @Test
  public void checkSecondColorInstance() {
    Assert.assertSame(Color.WHITE, getBlackWhite(1).get());
    Assert.assertSame(Color.BLACK, getWhiteBlack(1).get());
  }

  @Test
  public void checkCachedColorInstance() {
    MixedColorProducer producer = getTransparentRed(.999);
    Color color = producer.get();
    producer.setMixer(.999);
    Assert.assertEquals(color, producer.get());
    Assert.assertSame(color, producer.get());
    producer.setMixer(.9999);
    Assert.assertEquals(color, producer.get());
    Assert.assertNotSame(color, producer.get());
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
  private static MixedColorProducer getBlackWhite(double mixer) {
    return new MixedColorProducer(Color.BLACK, Color.WHITE, mixer);
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
    MixedColorProducer producer = getBlackWhite(0);
    for (int i = 0; i <= 0xFF; i++) {
      producer.setMixer((float)i / 0xFF);
      assertGrayColor(producer, i);
    }
  }


  @NotNull
  private static MixedColorProducer getWhiteBlack(double mixer) {
    return new MixedColorProducer(Color.WHITE, Color.BLACK, mixer);
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
    MixedColorProducer producer = getWhiteBlack(0);
    for (int i = 0; i <= 0xFF; i++) {
      producer.setMixer((float)i / 0xFF);
      assertGrayColor(producer, 0xFF - i);
    }
  }


  @NotNull
  private static MixedColorProducer getTransparentRed(double mixer) {
    return new MixedColorProducer(new Color(0xFF, 0, 0, 0), Color.RED, mixer);
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


  private static void assertColor(@NotNull MixedColorProducer producer, int expected) {
    assertColor(producer, new Color(expected, false));
  }

  private static void assertColorWithAlpha(@NotNull MixedColorProducer producer, int expected) {
    assertColor(producer, new Color(expected, true));
  }

  private static void assertGrayColor(@NotNull MixedColorProducer producer, int expected) {
    assertColor(producer, new Color(expected, expected, expected));
  }

  private static void assertColor(@NotNull MixedColorProducer producer, @NotNull Color expected) {
    Assert.assertEquals(expected, producer.get());
  }
}
