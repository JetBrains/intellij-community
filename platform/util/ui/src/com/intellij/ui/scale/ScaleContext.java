// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.scale;

import com.intellij.ui.JreHiDpiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.function.Function;

import static com.intellij.ui.scale.DerivedScaleType.DEV_SCALE;
import static com.intellij.ui.scale.ScaleType.SYS_SCALE;
import static com.intellij.ui.scale.ScaleType.USR_SCALE;

/**
 * Extends {@link UserScaleContext} with the system scale, and is thus used for raster-based painting.
 * The context is created via a context provider. If the provider is {@link Component}, the context's
 * system scale can be updated via a call to {@link #update()}, reflecting the current component's
 * system scale (which may change as the component moves b/w devices).
 *
 * @see ScaleContextAware
 * @author tav
 */
@SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "deprecation"})
public class ScaleContext extends UserScaleContext {
  protected Scale sysScale = SYS_SCALE.of(JBUIScale.sysScale());

  protected @Nullable WeakReference<Component> compRef;

  protected ScaleContext() {
    pixScale = derivePixScale();
  }

  protected ScaleContext(@NotNull Scale scale) {
    this();
    setScale(scale);
  }

  /**
   * Creates a context with all scale factors set to 1.
   */
  public static @NotNull ScaleContext createIdentity() {
    return create(USR_SCALE.of(1), SYS_SCALE.of(1));
  }

  /**
   * Creates a context from the provided {@code ctx}.
   */
  public static @NotNull ScaleContext create(@Nullable UserScaleContext ctx) {
    ScaleContext c = create();
    c.update(ctx);
    return c;
  }

  /**
   * Creates a context based on the comp's system scale and sticks to it via the {@link #update()} method.
   */
  public static @NotNull ScaleContext create(@Nullable Component comp) {
    final ScaleContext ctx = new ScaleContext(SYS_SCALE.of(JBUIScale.sysScale(comp)));
    if (comp != null) ctx.compRef = new WeakReference<>(comp);
    return ctx;
  }

  /**
   * Creates a context based on the gc's system scale
   */
  public static @NotNull ScaleContext create(@Nullable GraphicsConfiguration gc) {
    return new ScaleContext(SYS_SCALE.of(JBUIScale.sysScale(gc)));
  }

  /**
   * Creates a context based on the g's system scale
   */
  public static @NotNull ScaleContext create(Graphics2D g) {
    return new ScaleContext(SYS_SCALE.of(JBUIScale.sysScale(g)));
  }

  /**
   * Creates a context with the provided scale
   */
  public static @NotNull ScaleContext create(@NotNull Scale scale) {
    return new ScaleContext(scale);
  }

  /**
   * Creates a context with the provided scale factors
   */
  public static @NotNull ScaleContext create(Scale @NotNull ... scales) {
    ScaleContext ctx = create();
    for (Scale s : scales) ctx.setScale(s);
    return ctx;
  }

  /**
   * Creates a default context with the default screen scale and the current user scale
   */
  public static @NotNull ScaleContext create() {
    return new ScaleContext();
  }

  @Override
  protected double derivePixScale() {
    return getScale(DEV_SCALE) * super.derivePixScale();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double getScale(@NotNull ScaleType type) {
    if (type == SYS_SCALE) return sysScale.value;
    return super.getScale(type);
  }

  @Override
  protected @NotNull Scale getScaleObject(@NotNull ScaleType type) {
    if (type == SYS_SCALE) return sysScale;
    return super.getScaleObject(type);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public double getScale(@NotNull DerivedScaleType type) {
    switch (type) {
      case DEV_SCALE:
        return JreHiDpiUtil.isJreHiDPIEnabled() ? sysScale.value : 1;
      case EFF_USR_SCALE: return usrScale.value * objScale.value;
      case PIX_SCALE: return pixScale;
    }
    return 1f; // unreachable
  }

  /**
   * {@inheritDoc}
   * Also updates the system scale (if the context was created from Component) if necessary.
   */
  @Override
  public boolean update() {
    boolean updated = setScale(USR_SCALE.of(JBUIScale.scale(1f)));
    if (compRef != null) {
      Component component = compRef.get();
      if (component != null) {
        updated = setScale(SYS_SCALE.of(JBUIScale.sysScale(component.getGraphicsConfiguration()))) || updated;
      }
    }
    return onUpdated(updated);
  }

  /**
   * {@inheritDoc}
   * Also includes the system scale.
   */
  @Override
  public boolean setScale(@NotNull Scale scale) {
    if (isScaleOverridden(scale)) {
      return false;
    }

    if (scale.type == SYS_SCALE) {
      boolean updated = !sysScale.equals(scale);
      sysScale = scale;
      return onUpdated(updated);
    }
    return super.setScale(scale);
  }

  @Override
  protected <T extends UserScaleContext> boolean updateAll(@NotNull T scaleContext) {
    boolean updated = super.updateAll(scaleContext);
    if (!(scaleContext instanceof ScaleContext)) {
      return updated;
    }
    ScaleContext context = (ScaleContext)scaleContext;

    if (compRef != null) {
      compRef.clear();
    }
    compRef = context.compRef;

    return setScale(context.sysScale) || updated;
  }

  @Override
  public boolean equals(Object obj) {
    if (super.equals(obj) && obj instanceof ScaleContext) {
      ScaleContext that = (ScaleContext)obj;
      return that.sysScale.value == sysScale.value;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Double.hashCode(sysScale.value) * 31 + super.hashCode();
  }

  @Override
  public void dispose() {
    super.dispose();
    if (compRef != null) {
      compRef.clear();
    }
  }

  @Override
  public @NotNull <T extends UserScaleContext> T copy() {
    ScaleContext ctx = createIdentity();
    ctx.updateAll(this);
    //noinspection unchecked
    return (T)ctx;
  }

  @Override
  public String toString() {
    return usrScale + ", " + sysScale + ", " + objScale + ", " + pixScale;
  }

  @SuppressWarnings("ClassNameSameAsAncestorName")
  public static class Cache<D> extends UserScaleContext.Cache<D, ScaleContext> {
    public Cache(@NotNull Function<? super ScaleContext, ? extends D> dataProvider) {
      super(dataProvider);
    }
  }
}
