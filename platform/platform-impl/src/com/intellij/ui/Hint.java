// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @see LightweightHint
 */
public interface Hint {
  /**
   * @param parentComponent    defines coordinate system where hint will be shown.
   * @param x                  x coordinate of hint in parent coordinate system
   * @param y                  y coordinate of hint in parent coordinate system
   * @param focusBackComponent component which should get focus when the hint will
   */
  void show(@NotNull JComponent parentComponent, int x, int y, JComponent focusBackComponent, @NotNull HintHint hintInfo);

  /**
   * @return whether the hint is showing or not
   */
  boolean isVisible();

  /**
   * Hides current hint object.
   * <p/>
   * <b>Note:</b> this method is also used as a destruction callback, i.e. it performs necessary de-initialization when
   * current hint is not necessary to use anymore. Hence, it's <b>necessary</b> to call it from place where you definitely
   * know that current hint will not be used.
   */
  @RequiresEdt
  void hide();

  void addHintListener(@NotNull HintListener listener);

  void removeHintListener(@NotNull HintListener listener);

  void pack();

  void setLocation(@NotNull RelativePoint point);
}