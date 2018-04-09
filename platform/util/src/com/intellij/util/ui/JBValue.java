// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.paint.PaintUtil.RoundingMode;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND;

/**
 * A wrapper over unscaled value, applying {@link JBUI#scale} on request.
 *
 * @author tav
 */
public abstract class JBValue {
  protected final float value;

  protected JBValue(float value) {
    this.value = value;
  }

  /**
   * Returns scaled rounded to int value.
   */
  public int get() {
    // for backward compatibility rely on the rounding mode applied in JBUI.scale(int)
    return JBUI.scale((int)value);
  }

  /**
   * Returns scaled rounded to int (according to {@code rm}) value.
   */
  public int get(@NotNull RoundingMode rm) {
    return rm.round(JBUI.scale(value));
  }

  /**
   * JBValue int representation.
   */
  public static class Integer extends JBValue {
    /**
     * @param value unscaled value
     * @see JBUI#intValue(int)
     */
    public Integer(int value) {
      super(value);
    }

    @SuppressWarnings("unused")
    public int getUnscaled() {
      return (int)value;
    }
  }

  /**
   * JBValue float representation.
   */
  public static class Float extends JBValue {
    /**
     * @param value unscaled value
     * @see JBUI#floatValue(float)
     */
    public Float(float value) {
      super(value);
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

    @SuppressWarnings("unused")
    public float getUnscaled() {
      return value;
    }
  }
}
