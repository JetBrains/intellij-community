// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.paint.PaintUtil.RoundingMode;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedList;
import java.util.List;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND;

/**
 * A wrapper over an unscaled numeric value, auto-scaled via {@link JBUI#scale}.
 * <p>
 * Use {@link Integer} or {@link Float} classes for on-demand value scaling.
 * <p>
 * If the same JBValue object is used multiple times in a code block, then in order to save scaling ops,
 * a {@link CachedInteger} or {@link CachedFloat} classes can be used instead. Either with a separate
 * {@link UpdateTracker} (better to use for many JBValue objects), or with a dedicated one (for a single
 * JBValue object) as in the {@link SelfCachedInteger} or {@link SelfCachedFloat} classes.
 * <p>
 * Also, the {@link UIDefaultsInteger} class can be used for auto-scaling an integer value stored in
 * {@link UIDefaults}.
 *
 * @author tav
 * @see JBUI#intValue(int)
 * @see JBUI#intValue(int,UpdateTracker)
 * @see JBUI#floatValue(float)
 * @see JBUI#floatValue(float,UpdateTracker)
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

  public interface Cacheable {
    /**
     * Scales and caches the value.
     */
    void cache();
  }

  /**
   * JBValue wrapper over an integer value in {@link UIDefaults}.
   */
  public static class UIDefaultsInteger extends JBValue {
    private final @NotNull String key;

    public UIDefaultsInteger(@NotNull String key) {
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

  /**
   * Integer JBValue which caches its scaled value on JBUI.scale change.
   */
  public static class CachedInteger extends Integer implements Cacheable {
    private int cachedScaledValue;

    protected CachedInteger(int value) {
      super(value);
    }

    /**
     * @param value unscaled value
     * @param tracker updates the value
     * @see JBUI#intValue(int, UpdateTracker)
     */
    public CachedInteger(int value, @NotNull UpdateTracker tracker) {
      super(value);
      cachedScaledValue = JBUI.scale(value);
      tracker.track(this);
    }

    @Override
    public int get() {
      return cachedScaledValue;
    }

    public void cache() {
      cachedScaledValue = JBUI.scale((int)getUnscaled());
    }
  }

  /**
   * CachedInteger with a dedicated UpdateTracker.
   */
  public static class SelfCachedInteger extends CachedInteger {
    private final @NotNull UpdateTracker tracker = new UpdateTracker();

    public SelfCachedInteger(int value) {
      super(value);
      this.tracker.track(this);
    }
  }

  /**
   * Float JBValue which caches its scaled value on JBUI.scale change.
   */
  public static class CachedFloat extends Float implements Cacheable {
    private float cachedScaledValue;

    protected CachedFloat(float value) {
      super(value);
    }

    /**
     * @param value unscaled value
     * @param tracker updates the value
     * @see JBUI#floatValue(float, UpdateTracker)
     */
    public CachedFloat(float value, UpdateTracker tracker) {
      super(value);
      cachedScaledValue = JBUI.scale(value);
      tracker.track(this);
    }

    @Override
    public int get() {
      return ROUND.round(cachedScaledValue);
    }

    @Override
    public float getFloat() {
      return cachedScaledValue;
    }

    public void cache() {
      cachedScaledValue = JBUI.scale(getUnscaled());
    }
  }

  /**
   * CachedFloat with a dedicated UpdateTracker.
   */
  public static class SelfCachedFloat extends CachedFloat {
    private final @NotNull UpdateTracker tracker = new UpdateTracker();

    public SelfCachedFloat(float value) {
      super(value);
      this.tracker.track(this);
    }
  }

  /**
   * Tracks for a list of {@link Cacheable} values and auto-updates them on {@link JBUI#USER_SCALE_FACTOR_PROPERTY}} change.
   */
  public static class UpdateTracker {
    private final List<Cacheable> list = new LinkedList<Cacheable>();
    private final PropertyChangeListener listener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        for (Cacheable value : list) value.cache();
      }
    };

    public UpdateTracker() {
      JBUI.addPropertyChangeListener(JBUI.USER_SCALE_FACTOR_PROPERTY, listener);
    }

    public void track(Cacheable value) {
      list.add(value);
    }

    public void forget(Cacheable value) {
      list.remove(value);
    }

    public void dispose() {
      JBUI.removePropertyChangeListener(JBUI.USER_SCALE_FACTOR_PROPERTY, listener);
      list.clear();
    }
  }
}
