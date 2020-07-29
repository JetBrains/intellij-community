// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

public enum ThreeState {
  YES, NO, UNSURE;

  @NotNull
  public static ThreeState fromBoolean(boolean value) {
    return value ? YES : NO;
  }

  /**
   * Combine two different ThreeState values yielding UNSURE if values are different
   * and itself if values are the same.
   *
   * @param other other value to combine with this value
   * @return a result of combination of two ThreeState values
   */
  @NotNull
  public ThreeState merge(ThreeState other) {
    return this == other ? this : UNSURE;
  }

  public boolean toBoolean() {
    if (this == UNSURE) {
      throw new IllegalStateException("Must be or YES, or NO");
    }
    return this == YES;
  }

  /**
   * @return {@code YES} if the given states contain {@code YES}, otherwise {@code UNSURE} if the given states contain {@code UNSURE}, otherwise {@code NO}
   */
  @NotNull
  public static ThreeState mostPositive(@NotNull Iterable<ThreeState> states) {
    ThreeState result = NO;
    for (ThreeState state : states) {
      switch (state) {
        case YES: return YES;
        case UNSURE: result = UNSURE;
      }
    }
    return result;
  }

  /**
   * @return {@code UNSURE} if {@code states} contains different values, the single value otherwise
   * @throws IllegalArgumentException if {@code states} is empty
   */
  @NotNull
  public static ThreeState merge(@NotNull Iterable<ThreeState> states) {
    ThreeState result = null;
    for (ThreeState state : states) {
      if (state == UNSURE) {
        return UNSURE;
      }
      if (result == null) {
        result = state;
      }
      else if (result != state) {
        return UNSURE;
      }
    }
    if (result == null) throw new IllegalArgumentException("Argument should not be empty");
    return result;
  }
}