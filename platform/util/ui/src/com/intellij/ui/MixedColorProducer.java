// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * This is a color producer that allows to dynamically mix two colors.
 */
public final class MixedColorProducer {
  private final @NotNull Color first;
  private final @NotNull Color second;

  private Color cached;
  private double mixer;
  private int argb0;
  private int argb1;

  public MixedColorProducer(@NotNull Color color0, @NotNull Color color1) {
    first = color0;
    second = color1;
  }

  private void updateMixer(double value) {
    if (mixer != value) {
      mixer = value;
      cached = null;
    }
  }

  private void updateFirstARGB() {
    int value = first.getRGB();
    if (argb0 != value) {
      argb0 = value;
      cached = null;
    }
  }

  private void updateSecondARGB() {
    int value = second.getRGB();
    if (argb1 != value) {
      argb1 = value;
      cached = null;
    }
  }

  private int mix(int pos) {
    int value0 = 0xFF & (argb0 >> pos);
    int value1 = 0xFF & (argb1 >> pos);
    if (value0 == value1) return value0;
    return value0 + (int)Math.round(mixer * (value1 - value0));
  }

  public @NotNull Color produce(double mixer) {
    if (Double.isNaN(mixer) || mixer < 0 || 1 < mixer) throw new IllegalArgumentException("mixer[0..1] is " + mixer);
    if (mixer <= 0) return first;
    if (mixer >= 1) return second;

    updateMixer(mixer);
    updateFirstARGB();
    updateSecondARGB();

    Color result = cached;
    if (result == null) {
      //noinspection UseJBColor
      cached = result = new Color(mix(16), mix(8), mix(0), mix(24));
    }
    return result;
  }
}
