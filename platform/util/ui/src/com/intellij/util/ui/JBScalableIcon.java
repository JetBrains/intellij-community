// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.scale.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.ui.scale.DerivedScaleType.DEV_SCALE;
import static com.intellij.ui.scale.DerivedScaleType.PIX_SCALE;
import static com.intellij.ui.scale.ScaleType.*;

/**
 * A scalable icon, {@link UserScaleContext} aware, assuming vector-based painting (system scale independent).
 *
 * @author tav
 */
public abstract class JBScalableIcon extends UserScaleContextSupport implements ScalableIcon {
  private final Scaler myScaler = new Scaler() {
    @Override
    protected double currentScale() {
      if (autoUpdateScaleContext) getScaleContext().update();
      return getScale(USR_SCALE);
    }
  };
  private boolean autoUpdateScaleContext = true;

  public JBScalableIcon() {
  }

  protected JBScalableIcon(@NotNull JBScalableIcon icon) {
    this();
    updateScaleContext(icon.getScaleContext());
    myScaler.update(icon.myScaler);
    autoUpdateScaleContext = icon.autoUpdateScaleContext;
  }

  protected boolean isIconPreScaled() {
    return myScaler.isPreScaled();
  }

  protected void setIconPreScaled(boolean preScaled) {
    myScaler.setPreScaled(preScaled);
  }

  /**
   * The pre-scaled state of the icon indicates whether the initial size of the icon
   * is pre-scaled (by the global user scale) or not. If the size is not pre-scaled,
   * then there're two approaches to deal with it:
   * 1) scale its initial size right away and store;
   * 2) scale its initial size every time it's requested.
   * The 2nd approach is preferable because of the the following. Scaling of the icon may
   * involve not only USR_SCALE but OBJ_SCALE as well. In which case applying all the scale
   * factors and then rounding (the size is integer, the scale factors are not) gives more
   * accurate result than rounding and then scaling.
   * <p>
   * For example, say we have an icon of 15x15 initial size, USR_SCALE is 1.5f, OBJ_SCALE is 1,5f.
   * Math.round(Math.round(15 * USR_SCALE) * OBJ_SCALE) = 35
   * Math.round(15 * USR_SCALE * OBJ_SCALE) = 34
   * <p>
   * Thus, JBUI.scale(MyIcon.create(w, h)) is preferable to MyIcon.create(JBUI.scale(w), JBUI.scale(h)).
   * Here [w, h] is "raw" unscaled size.
   *
   * @param preScaled whether the icon is pre-scaled
   * @return the icon in the provided pre-scaled state
   * @see JBUI#scale(JBScalableIcon)
   */
  @NotNull
  public JBScalableIcon withIconPreScaled(boolean preScaled) {
    setIconPreScaled(preScaled);
    return this;
  }

  /**
   * Sets whether the scale context should be auto-updated by the {@link Scaler}.
   * This ensures that {@link #scaleVal(double)} always uses up-to-date scale.
   * This is useful when the icon doesn't need to recalculate its internal sizes
   * on the scale context update and so it doesn't need the result of the update
   * and/or it doesn't listen for updates. Otherwise, the value should be set to
   * false and the scale context should be updated manually.
   * <p>
   * By default the value is true.
   */
  protected void setAutoUpdateScaleContext(boolean autoUpdate) {
    autoUpdateScaleContext = autoUpdate;
  }

  @Override
  public float getScale() {
    return (float)getScale(OBJ_SCALE); // todo: float -> double
  }

  @Override
  @NotNull
  public Icon scale(float scale) {
    setScale(OBJ_SCALE.of(scale));
    return this;
  }

  /**
   * An equivalent of scaleVal(value, PIX_SCALE)
   */
  protected double scaleVal(double value) {
    return scaleVal(value, PIX_SCALE);
  }

  /**
   * Returns the value scaled according to the provided scale type
   */
  protected double scaleVal(double value, @NotNull ScaleType type) {
    switch (type) {
      case USR_SCALE: return myScaler.scaleVal(value);
      case SYS_SCALE: return value * getScale(SYS_SCALE);
      case OBJ_SCALE: return value * getScale(OBJ_SCALE);
    }
    return value; // unreachable
  }

  /**
   * Returns the value scaled according to the provided scale type
   */
  protected double scaleVal(double value, @NotNull DerivedScaleType type) {
    switch (type) {
      case DEV_SCALE: return value * getScale(DEV_SCALE);
      case EFF_USR_SCALE:
      case PIX_SCALE:
        return myScaler.scaleVal(value) * getScale(OBJ_SCALE);
    }
    return value; // unreachable
  }

  @Override
  public String toString() {
    return getClass().getName() + " " + getIconWidth() + "x" + getIconHeight();
  }
}


