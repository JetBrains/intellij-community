// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import org.jetbrains.annotations.NotNull;

/**
 * A wrapper over a user scale supplier, representing a state of a UI element
 * in which its initial size is either pre-scaled (according to {@link #currentScale()})
 * or not (given in a standard resolution, e.g. 16x16 for an icon).
 */
public abstract class Scaler {
  @SuppressWarnings("AbstractMethodCallInConstructor")
  protected double initialScale = currentScale();

  private double alignedScale() {
    return currentScale() / initialScale;
  }

  public boolean isPreScaled() {
    return initialScale != 1d;
  }

  public void setPreScaled(boolean preScaled) {
    initialScale = preScaled ? currentScale() : 1d;
  }

  /**
   * @param value the value (e.g. a size of the associated UI object) to scale
   * @return the scaled result, taking into account the pre-scaled state and {@link #currentScale()}
   */
  public double scaleVal(double value) {
    return value * alignedScale();
  }

  /**
   * Supplies the Scaler with the current user scale. This can be the current global user scale or
   * the context scale ({@link UserScaleContext#usrScale}) or something else.
   */
  protected abstract double currentScale();

  /**
   * Synchronizes the state with the provided scaler.
   *
   * @return whether the state has been updated
   */
  public boolean update(@NotNull Scaler scaler) {
    boolean updated = initialScale != scaler.initialScale;
    initialScale = scaler.initialScale;
    return updated;
  }
}
