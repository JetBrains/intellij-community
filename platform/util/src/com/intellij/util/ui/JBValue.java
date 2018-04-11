// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.paint.PaintUtil.RoundingMode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND;

/**
 * A wrapper over an unscaled value which lazily scales it via {@link JBUI#scale}.
 *
 * @author tav
 */
public abstract class JBValue {
  protected JBValue() {}

  /**
   * Returns scaled rounded to int value.
   */
  public int get() {
    // for backward compatibility rely on the rounding mode applied in JBUI.scale(int)
    return JBUI.scale((int)getUnscaled());
  }

  /**
   * Returns scaled rounded to int (according to {@code rm}) value.
   */
  public int get(@NotNull RoundingMode rm) {
    return rm.round(JBUI.scale(getUnscaled()));
  }

  /**
   * Returns initial unscaled value.
   */
  protected abstract float getUnscaled();

  /**
   * JBValue wrapper over an integer value in {@link UIManager}.
   */
  public static class UIManagerInteger extends JBValue {
    private final String key;

    public UIManagerInteger(@NotNull String key) {
      this.key = key;
    }

    @Override
    protected float getUnscaled() {
      return UIManager.getInt(key);
    }
  }

  /**
   * JBValue wrapper over an integer.
   */
  public static class Integer extends JBValue {
    private final int value;

    /**
     * @param value unscaled value
     * @see JBUI#intValue(int)
     */
    public Integer(int value) {
      this.value = value;
    }

    @Override
    protected float getUnscaled() {
      return value;
    }
  }

  /**
   * JBValue wrapper over a float.
   */
  public static class Float extends JBValue {
    private final float value;

    /**
     * @param value unscaled value
     * @see JBUI#floatValue(float)
     */
    public Float(float value) {
      this.value = value;
    }

    @Override
    public int get() {
      return ROUND.round(JBUI.scale(value));
    }

    /**
     * Scales the value and returns.
     */
    public float getFloat() {
      return JBUI.scale(value);
    }

    @Override
    protected float getUnscaled() {
      return value;
    }
  }
}
