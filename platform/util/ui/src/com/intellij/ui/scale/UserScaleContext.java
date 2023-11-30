// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.scale;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

import static com.intellij.ui.scale.ScaleType.*;

/**
 * Represents a snapshot of the user space scale factors: {@link ScaleType#USR_SCALE} and {@link ScaleType#OBJ_SCALE}).
 * The context can be associated with a UI object (see {@link ScaleContextAware}) to define its HiDPI behavior.
 * Unlike {@link ScaleContext}, UserScaleContext is device scale independent and is thus used for vector-based painting.
 *
 * @see ScaleContext
 * @author tav
 */
public class UserScaleContext {
  protected Scale usrScale = USR_SCALE.of(JBUIScale.scale(1f));
  protected Scale objScale = OBJ_SCALE.of(1);
  protected double pixScale = usrScale.value;

  private List<UpdateListener> listeners;
  protected @Nullable EnumSet<ScaleType> overriddenScales;

  private static final Scale @NotNull [] EMPTY_SCALE_ARRAY = new Scale[]{};

  protected UserScaleContext() {
  }

  /**
   * Creates a context with all scale factors set to 1.
   */
  public static @NotNull UserScaleContext createIdentity() {
    return create(USR_SCALE.of(1));
  }

  /**
   * Creates a context with the provided scale factors (a system scale is ignored)
   */
  public static @NotNull UserScaleContext create(Scale @NotNull ... scales) {
    UserScaleContext ctx = create();
    for (Scale s : scales) {
      ctx.setScale(s);
    }
    return ctx;
  }

  /**
   * Creates a default context with the current user scale
   */
  public static @NotNull UserScaleContext create() {
    return new UserScaleContext();
  }

  /**
   * Creates a context from the provided {@code ctx}.
   */
  public static @NotNull UserScaleContext create(@Nullable UserScaleContext ctx) {
    UserScaleContext c = createIdentity();
    c.update(ctx);
    return c;
  }

  protected double derivePixScale() {
    return usrScale.value * objScale.value;
  }

  /**
   * Permanently overrides the provided scale (the scale won't be changed on subsequent {@link #update()}).
   * Can be used to make a UI object user scale independent:
   * <p></p>
   * <code>
   * ((ScaleContextAware)uiObject).getScaleContext().overrideScale(USR_SCALE.of(1.0));
   * </code>
   *
   * @param scale the new scale to override
   * @return whether the new scale updated the current value
   * @see ScaleType#of(double)
   */
  public boolean overrideScale(@NotNull Scale scale) {
    if (overriddenScales != null) {
      overriddenScales.remove(scale.type); // previous override should not prevent this override
    }
    boolean updated = setScale(scale);

    if (overriddenScales == null) {
      overriddenScales = EnumSet.of(scale.type);
    }
    else {
      overriddenScales.add(scale.type);
    }
    return updated;
  }

  protected boolean isScaleOverridden(@NotNull Scale scale) {
    return overriddenScales != null && overriddenScales.contains(scale.type);
  }

  protected Scale @NotNull [] getOverriddenScales() {
    if (overriddenScales == null) {
      return EMPTY_SCALE_ARRAY;
    }

    Scale[] scales = new Scale[overriddenScales.size()];
    int i = 0;
    for (ScaleType type : overriddenScales) {
      scales[i++] = getScaleObject(type);
    }
    return scales;
  }

  /**
   * Sets the new scale (a system scale is ignored).
   * Use {@link ScaleType#of(double)} to provide the new scale.
   * Note, the new scale value can be changed on subsequent {@link #update()}.
   * Use {@link #overrideScale(Scale)}
   * to set a scale permanently.
   *
   * @param scale the new scale to set
   * @return whether the new scale updated the current value
   * @see ScaleType#of(double)
   */
  public boolean setScale(@NotNull Scale scale) {
    if (isScaleOverridden(scale)) {
      return false;
    }

    boolean updated = false;
    switch (scale.type) {
      case USR_SCALE -> {
        updated = !usrScale.equals(scale);
        usrScale = scale;
      }
      case OBJ_SCALE -> {
        updated = !objScale.equals(scale);
        objScale = scale;
      }
      case SYS_SCALE -> {
        return false;
      }
    }
    return onUpdated(updated);
  }

  /**
   * @return the context scale factor of the provided type (1d for a system scale)
   */
  public double getScale(@NotNull ScaleType type) {
    return switch (type) {
      case USR_SCALE -> usrScale.value;
      case SYS_SCALE -> 1d;
      case OBJ_SCALE -> objScale.value;
    };
  }

  protected @NotNull Scale getScaleObject(@NotNull ScaleType type) {
    return switch (type) {
      case USR_SCALE -> usrScale;
      case SYS_SCALE -> SYS_SCALE.of(1);
      case OBJ_SCALE -> objScale;
    };
  }

  public double getScale(@NotNull DerivedScaleType type) {
    return switch (type) {
      case DEV_SCALE -> 1;
      case PIX_SCALE, EFF_USR_SCALE -> pixScale;
    };
  }

  /**
   * Applies the scale of the provided type to {@code value} and returns the result.
   */
  public double apply(double value, DerivedScaleType type) {
    return value * getScale(type);
  }

  /**
   * Applies the scale of the provided type to {@code value} and returns the result.
   */
  public double apply(double value, ScaleType type) {
    return value * getScale(type);
  }

  protected boolean onUpdated(boolean updated) {
    if (updated) {
      pixScale = derivePixScale();
      notifyUpdateListeners();
    }
    return updated;
  }

  /**
   * Updates the user scale with the current global user scale if necessary.
   *
   * @return whether any of the scale factors has been updated
   */
  public boolean update() {
    return onUpdated(setScale(USR_SCALE.of(JBUIScale.scale(1f))));
  }

  /**
   * Updates the context with the state of the provided one.
   *
   * @param scaleContext the new context
   * @return whether any of the scale factors has been updated
   */
  public boolean update(@Nullable UserScaleContext scaleContext) {
    return scaleContext == null ? update() : onUpdated(updateAll(scaleContext));
  }

  protected <T extends UserScaleContext> boolean updateAll(@NotNull T scaleContext) {
    boolean updated = setScale(scaleContext.usrScale);
    updated = setScale(scaleContext.objScale) || updated;

    // merge this.overriddenScales with scaleContext.overriddenScales
    for (Scale scale : scaleContext.getOverriddenScales()) {
      overrideScale(scale);
    }
    return updated;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof UserScaleContext that)) return false;

    return that.usrScale.value == usrScale.value &&
           that.objScale.value == objScale.value;
  }

  @Override
  public int hashCode() {
    return Double.hashCode(usrScale.value) * 31 + Double.hashCode(objScale.value);
  }

  /**
   * Clears the links.
   */
  public void dispose() {
    listeners = null;
  }

  /**
   * A context update listener. Used to listen to possible external context updates.
   */
  public interface UpdateListener {
    void contextUpdated();
  }

  public void addUpdateListener(@NotNull UpdateListener l) {
    if (listeners == null) {
      listeners = new SmartList<>(l);
    }
    else {
      listeners.add(l);
    }
  }

  @SuppressWarnings("unused")
  public void removeUpdateListener(@NotNull UpdateListener l) {
    if (listeners != null) listeners.remove(l);
  }

  protected void notifyUpdateListeners() {
    if (listeners != null) {
      listeners.forEach(UpdateListener::contextUpdated);
    }
  }

  public @NotNull <T extends UserScaleContext> T copy() {
    UserScaleContext ctx = createIdentity();
    ctx.updateAll(this);
    //noinspection unchecked
    return (T)ctx;
  }

  @Override
  public String toString() {
    return usrScale + ", " + objScale + ", " + pixScale;
  }
}
