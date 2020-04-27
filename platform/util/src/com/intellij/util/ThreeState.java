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
}