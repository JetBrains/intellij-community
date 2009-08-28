package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface Hint {
  /**
   * @param parentComponent defines coordinate system where hint will be shown.
   * Cannot be <code>null</code>.
   *
   * @param x x coordinate of hint in parent coordinate system
   *
   * @param y y coordinate of hint in parent coordinate system
   *
   * @param focusBackComponent component which should get focus when the hint will
   * be hidden. If <code>null</code> then the hint doesn't manage focus after closing.
   */
  void show(@NotNull JComponent parentComponent, int x, int y, JComponent focusBackComponent);

  /**
   * @return whether the hint is showing or not
   */
  boolean isVisible();

  void hide();

  void addHintListener(HintListener listener);

  void removeHintListener(HintListener listener);
}