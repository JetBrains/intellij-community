// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedList;
import java.util.List;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND;

/**
 * A wrapper over an unscaled numeric value, auto-scaled via {@link JBUIScale#scale}.
 * <p>
 * {@code JBValue} can be used separately or in a group, see {@link JBValueGroup}.
 * <p>
 * Also, a {@link UIInteger} value can be used as a wrapper over an integer value stored in {@link UIDefaults}.
 *
 * @see JBUI#value(float)
 * @see JBValueGroup#value(float)
 * @see JBUI#uiIntValue(String, int)
 *
 * @author tav
 */
public abstract class JBValue {
  protected JBValue() {}

  /**
   * Returns scaled rounded to int value.
   */
  public int get() {
    return ROUND.round(JBUIScale.scale(getUnscaled()));
  }

  /**
   * Returns scaled float value.
   */
  public float getFloat() {
    return JBUIScale.scale(getUnscaled());
  }

  /**
   * Returns scaled rounded to int (according to {@code rm}) value.
   */
  public int get(@NotNull RoundingMode rm) {
    return rm.round(JBUIScale.scale(getUnscaled()));
  }

  /**
   * Returns initial unscaled value.
   */
  public abstract float getUnscaled();

  /**
   * JBValue wrapper over an integer value in {@link UIDefaults}.
   *
   * @see JBUI#uiIntValue(String,int)
   */
  public static final class UIInteger extends JBValue {
    private final @NotNull String key;
    private final int defValue;

    public UIInteger(@NotNull String key, int defValue) {
      this.key = key;
      this.defValue = defValue;
    }

    @Override
    public float getUnscaled() {
      return JBUI.getInt(key, defValue);
    }
  }

  /**
   * JBValue wrapper over a float.
   *
   * @see JBUI#value(float)
   */
  public static class Float extends JBValue {
    private final float value;

    /**
     * @param value unscaled value
     */
    public Float(float value) {
      this(value, false);
    }

    /**
     * @param value unscaled or pre-scaled value
     */
    public Float(float value, boolean preScaled) {
      this.value = preScaled ? value / JBUIScale.scale(1f) : value;
    }

    @Override
    public float getUnscaled() {
      return value;
    }
  }

  private static class CachedFloat extends Float {
    private float cachedScaledValue;

    protected CachedFloat(float value) {
      super(value);
      scaleAndCache();
    }

    @Override
    public int get() {
      return ROUND.round(cachedScaledValue);
    }

    @Override
    public float getFloat() {
      return cachedScaledValue;
    }

    @Override
    public int get(@NotNull RoundingMode rm) {
      return rm.round(cachedScaledValue);
    }

    public void scaleAndCache() {
      cachedScaledValue = JBUIScale.scale(getUnscaled());
    }
  }

  /**
   * A group of values, utilizing caching strategy per value. The group listens to the global user scale factor change and updates
   * all of the values. The {@link JBValue#get()} method of a value returns a cached scaled value, saving recalculation.
   * This can be a better choice when values are used multiple times in a code block.
   */
  public static class JBValueGroup {
    private final List<CachedFloat> group = new LinkedList<>();
    private final PropertyChangeListener listener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        for (CachedFloat value : group) value.scaleAndCache();
      }
    };

    public JBValueGroup() {
      JBUIScale.addUserScaleChangeListener(listener);
    }

    /**
     * Creates {@link JBValue} and adds it to this group.
     */
    public JBValue value(float value) {
      CachedFloat v = new CachedFloat(value);
      group.add(v);
      return v;
    }

    public void dispose() {
      JBUIScale.removeUserScaleChangeListener(listener);
      group.clear();
    }
  }
}
