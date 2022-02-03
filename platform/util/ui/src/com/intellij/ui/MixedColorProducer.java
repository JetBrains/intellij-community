// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.Couple;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.function.Supplier;

/**
 * This is a color producer that allows dynamically mix two colors.
 */
public final class MixedColorProducer implements Supplier<Color> {
  private final Couple<Color> couple;
  private double mixer;
  private Color cached;
  private int argb0;
  private int argb1;

  public MixedColorProducer(@NotNull Color color0, @NotNull Color color1) {
    couple = Couple.of(color0, color1);
  }

  public MixedColorProducer(@NotNull Color color0, @NotNull Color color1, double mixer) {
    this(color0, color1);
    setMixer(mixer);
  }

  public void setMixer(double value) {
    if (Double.isNaN(value) || value < 0 || 1 < value) throw new IllegalArgumentException("mixer[0..1] is " + value);
    if (mixer != value) {
      mixer = value;
      cached = null;
    }
  }

  private void updateFirstARGB() {
    int value = couple.first.getRGB();
    if (argb0 != value) {
      argb0 = value;
      cached = null;
    }
  }

  private void updateSecondARGB() {
    int value = couple.second.getRGB();
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
    setMixer(mixer);
    return get();
  }

  @Override
  public @NotNull Color get() {
    if (mixer <= 0) return couple.first;
    if (mixer >= 1) return couple.second;
    updateFirstARGB();
    updateSecondARGB();
    if (cached == null) {
      //noinspection UseJBColor
      cached = new Color(mix(16), mix(8), mix(0), mix(24));
    }
    return cached;
  }
}
