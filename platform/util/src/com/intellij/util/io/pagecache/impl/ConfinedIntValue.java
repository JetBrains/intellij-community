// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import com.intellij.util.MathUtil;

import java.util.function.IntSupplier;

/**
 * Value what is confined to a [min, max] range.
 * Increments/decrements/updates keep value in that range.
 */
public final class ConfinedIntValue implements IntSupplier {
  private int value;

  private final int minValue;
  private final int maxValue;

  public ConfinedIntValue(int value,
                          int minValue,
                          int maxValue) {
    this.value = value;
    this.minValue = minValue;
    this.maxValue = maxValue;
  }

  @Override
  public int getAsInt() {
    return value();
  }

  public int value() {
    return value;
  }

  public void inc() {
    update(value + 1);
  }

  public void dec() {
    update(value - 1);
  }

  public void update(int newValue) {
    value = MathUtil.clamp(newValue, minValue, maxValue);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ConfinedIntValue value1 = (ConfinedIntValue)o;

    if (value != value1.value) return false;
    if (minValue != value1.minValue) return false;
    if (maxValue != value1.maxValue) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return 31 * (31 * value + minValue) + maxValue;
  }

  @Override
  public String toString() {
    return "ConfinedIntValue[=" + value + "]" +
           "[" + minValue + ".." + maxValue + ']';
  }
}
