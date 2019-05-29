// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.Pair;
import gnu.trove.TDoubleObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.ImageObserver;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.intellij.util.ui.JBUIScale.DerivedScaleType.DEV_SCALE;
import static com.intellij.util.ui.JBUIScale.DerivedScaleType.PIX_SCALE;
import static com.intellij.util.ui.JBUIScale.ScaleType.*;

/**
 * Base classes for UI scaling.
 *
 * @author tav
 */
public class JBUIScale {
  /**
   * The IDE supports two different HiDPI modes:
   *
   * 1) IDE-managed HiDPI mode.
   *
   * Supported for backward compatibility until complete transition to the JRE-managed HiDPI mode happens.
   * In this mode there's a single coordinate space and the whole UI is scaled by the IDE guided by the
   * user scale factor ({@link #USR_SCALE}).
   *
   * 2) JRE-managed HiDPI mode.
   *
   * In this mode the JRE scales graphics prior to drawing it on the device. So, there're two coordinate
   * spaces: the user space and the device space. The system scale factor ({@link #SYS_SCALE}) defines the
   * transform b/w the spaces. The UI size metrics (windows, controls, fonts height) are in the user
   * coordinate space. Though, the raster images should be aware of the device scale in order to meet
   * HiDPI. (For instance, JRE on a Mac Retina monitor device works in the JRE-managed HiDPI mode,
   * transforming graphics to the double-scaled device coordinate space)
   *
   * The IDE operates the scale factors of the following types:
   *
   * 1) The user scale factor: {@link #USR_SCALE}
   * 2) The system (monitor device) scale factor: {@link #SYS_SCALE}
   * 3) The object (UI instance specific) scale factor: {@link #OBJ_SCALE}
   *
   * @see UIUtil#isJreHiDPIEnabled()
   * @see UIUtil#isJreHiDPI()
   * @see UIUtil#isJreHiDPI(GraphicsConfiguration)
   * @see UIUtil#isJreHiDPI(Graphics2D)
   * @see JBUI#isUsrHiDPI()
   * @see UIUtil#drawImage(Graphics, Image, Rectangle, Rectangle, ImageObserver)
   * @see UIUtil#createImage(Graphics, int, int, int)
   * @see UIUtil#createImage(GraphicsConfiguration, int, int, int)
   * @see UIUtil#createImage(int, int, int)
   * @see ScaleContext
   */
  public enum ScaleType {
    /**
     * The user scale factor is set and managed by the IDE. Currently it's derived from the UI font size,
     * specified in the IDE Settings.
     *
     * The user scale value depends on which HiDPI mode is enabled. In the IDE-managed HiDPI mode the
     * user scale "includes" the default system scale and simply equals it with the default UI font size.
     * In the JRE-managed HiDPI mode the user scale is independent of the system scale and equals 1.0
     * with the default UI font size. In case the default UI font size changes, the user scale changes
     * proportionally in both the HiDPI modes.
     *
     * In the IDE-managed HiDPI mode the user scale completely defines the UI scale. In the JRE-managed
     * HiDPI mode the user scale can be considered a supplementary scale taking effect in cases like
     * the IDE Presentation Mode and when the default UI scale is changed by the user.
     *
     * @see JBUI#setUserScaleFactor(float)
     * @see JBUI#scale(float)
     * @see JBUI#scale(int)
     */
    USR_SCALE,
    /**
     * The system scale factor is defined by the device DPI and/or the system settings. For instance,
     * Mac Retina monitor device has the system scale 2.0 by default. As there can be multiple devices
     * (multi-monitor configuration) there can be multiple system scale factors, appropriately. However,
     * there's always a single default system scale factor corresponding to the default device. And it's
     * the only system scale available in the IDE-managed HiDPI mode.
     *
     * In the JRE-managed HiDPI mode, the system scale defines the scale of the transform b/w the user
     * and the device coordinate spaces performed by the JRE.
     *
     * @see JBUI#sysScale()
     * @see JBUI#sysScale(GraphicsConfiguration)
     * @see JBUI#sysScale(Graphics2D)
     * @see JBUI#sysScale(Component)
     */
    SYS_SCALE,
    /**
     * An extra scale factor of a particular UI object, which doesn't affect any other UI object, as opposed
     * to the user scale and the system scale factors. This scale factor affects the user space size of the object
     * and doesn't depend on the HiDPI mode. By default it is set to 1.0.
     */
    OBJ_SCALE;

    @NotNull
    public Scale of(double value) {
      return Scale.create(value, this);
    }
  }

  /**
   * The scale factors derived from the {@link ScaleType} scale factors. Used for convenience.
   */
  public enum DerivedScaleType {
    /**
     * The effective user scale factor "combines" all the user space scale factors which are: {@code USR_SCALE} and {@code OBJ_SCALE}.
     * So, basically it equals {@code USR_SCALE} * {@code OBJ_SCALE}.
     */
    EFF_USR_SCALE,
    /**
     * The device scale factor. In JRE-HiDPI mode equals {@link ScaleType#SYS_SCALE}, in IDE-HiDPI mode equals 1.0
     * (in IDE-HiDPI the user space and the device space are equal and so the transform b/w the spaces is 1.0)
     */
    DEV_SCALE,
    /**
     * The pixel scale factor "combines" all the other scale factors (user, system and object) and defines the
     * effective scale of a particular UI object.
     *
     * For instance, on Mac Retina monitor (JRE-managed HiDPI) in the Presentation mode (which, say,
     * doubles the UI scale) the pixel scale would equal 4.0 (provided the object scale is 1.0). The value
     * is the product of the user scale 2.0 and the system scale 2.0. In the IDE-managed HiDPI mode,
     * the pixel scale equals {@link #EFF_USR_SCALE}.
     *
     * @see JBUI#pixScale()
     * @see JBUI#pixScale(GraphicsConfiguration)
     * @see JBUI#pixScale(Graphics2D)
     * @see JBUI#pixScale(Component)
     * @see JBUI#pixScale(float)
     */
    PIX_SCALE
  }

  /**
   * A scale factor value of {@link ScaleType}.
   */
  public static class Scale {
    private final double value;
    private final ScaleType type;

    // The cache radically reduces potentially thousands of equal Scale instances.
    private static final ThreadLocal<EnumMap<ScaleType, TDoubleObjectHashMap<Scale>>> cache =
      ThreadLocal.withInitial(() -> new EnumMap<>(ScaleType.class));

    @NotNull
    public static Scale create(double value, @NotNull ScaleType type) {
      EnumMap<ScaleType, TDoubleObjectHashMap<Scale>> emap = cache.get();
      TDoubleObjectHashMap<Scale> map = emap.get(type);
      if (map == null) {
        emap.put(type, map = new TDoubleObjectHashMap<>());
      }
      Scale scale = map.get(value);
      if (scale != null) return scale;
      map.put(value, scale = new Scale(value, type));
      return scale;
    }

    private Scale(double value, @NotNull ScaleType type) {
      this.value = value;
      this.type = type;
    }

    public double value() {
      return value;
    }

    @NotNull
    public ScaleType type() {
      return type;
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj; // can rely on default impl due to caching
    }

    @Override
    public String toString() {
      return "[" + type.name() + " " + value + "]";
    }
  }

  /**
   * A wrapper over a user scale supplier, representing a state of a UI element
   * in which its initial size is either pre-scaled (according to {@link #currentScale()})
   * or not (given in a standard resolution, e.g. 16x16 for an icon).
   */
  public abstract static class Scaler {
    @SuppressWarnings("AbstractMethodCallInConstructor")
    protected double initialScale = currentScale();

    private double alignedScale() {
      return currentScale() / initialScale;
    }

    protected boolean isPreScaled() {
      return initialScale != 1d;
    }

    protected void setPreScaled(boolean preScaled) {
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

  // [tav] todo: [User]ScaleContext is thread-unsafe, should it be thread-safe?
  /**
   * Represents a snapshot of the user space scale factors: {@link ScaleType#USR_SCALE} and {@link ScaleType#OBJ_SCALE}).
   * The context can be associated with a UI object (see {@link ScaleContextAware}) to define its HiDPI behaviour.
   * Unlike {@link ScaleContext}, UserScaleContext is device scale independent and is thus used for vector-based painting.
   *
   * @see ScaleContextAware
   * @see ScaleContext
   */
  public static class UserScaleContext {
    protected Scale usrScale = USR_SCALE.of(JBUI.scale(1f));
    protected Scale objScale = OBJ_SCALE.of(1d);
    protected double pixScale = usrScale.value;

    private List<UpdateListener> listeners;
    private EnumSet<ScaleType> overriddenScales;

    protected UserScaleContext() {
    }

    /**
     * Creates a context with all scale factors set to 1.
     */
    @NotNull
    public static UserScaleContext createIdentity() {
      return create(USR_SCALE.of(1));
    }

    /**
     * Creates a context with the provided scale factors (system scale is ignored)
     */
    @NotNull
    public static UserScaleContext create(@NotNull Scale... scales) {
      UserScaleContext ctx = create();
      for (Scale s : scales) ctx.setScale(s);
      return ctx;
    }

    /**
     * Creates a default context with the current user scale
     */
    @NotNull
    public static UserScaleContext create() {
      return new UserScaleContext();
    }

    /**
     * Creates a context from the provided {@code ctx}.
     */
    @NotNull
    public static UserScaleContext create(@Nullable UserScaleContext ctx) {
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
     * <p>
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

    /**
     * Sets the new scale (system scale is ignored). Use {@link ScaleType#of(double)} to provide the new scale.
     * Note, the new scale value can be change on subsequent {@link #update()}. Use {@link #overrideScale(Scale)}
     * to set a scale permanently.
     *
     * @param scale the new scale to set
     * @return whether the new scale updated the current value
     * @see ScaleType#of(double)
     */
    public boolean setScale(@NotNull Scale scale) {
      if (isScaleOverridden(scale)) return false;

      boolean updated = false;
      switch (scale.type) {
        case USR_SCALE:
          updated = !usrScale.equals(scale);
          usrScale = scale;
          break;
        case OBJ_SCALE:
          updated = !objScale.equals(scale);
          objScale = scale;
          break;
        case SYS_SCALE: return false;
      }
      return onUpdated(updated);
    }

    /**
     * @return the context scale factor of the provided type (1d for system scale)
     */
    public double getScale(@NotNull ScaleType type) {
      switch (type) {
        case USR_SCALE: return usrScale.value;
        case SYS_SCALE: return 1d;
        case OBJ_SCALE: return objScale.value;
      }
      return 1f; // unreachable
    }

    public double getScale(@NotNull DerivedScaleType type) {
      switch (type) {
        case DEV_SCALE: return 1;
        case PIX_SCALE:
        case EFF_USR_SCALE:
          return pixScale;
      }
      return 1f; // unreachable
    }

    /**
     * Applies the scale of the provided type to {@code value} and returns the result.
     */
    public double apply(double value, DerivedScaleType type) {
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
      return onUpdated(setScale(USR_SCALE.of(JBUI.scale(1f))));
    }

    /**
     * Updates the context with the state of the provided one.
     *
     * @param ctx the new context
     * @return whether any of the scale factors has been updated
     */
    public boolean update(@Nullable UserScaleContext ctx) {
      if (ctx == null) return update();
      return onUpdated(updateAll(ctx));
    }

    protected <T extends UserScaleContext> boolean updateAll(@NotNull T ctx) {
      boolean updated = setScale(ctx.usrScale);
      return setScale(ctx.objScale) || updated;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (!(obj instanceof UserScaleContext)) return false;

      UserScaleContext that = (UserScaleContext)obj;
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
      if (listeners == null) listeners = new ArrayList<>(1);
      listeners.add(l);
    }

    @SuppressWarnings("unused")
    public void removeUpdateListener(@NotNull UpdateListener l) {
      if (listeners != null) listeners.remove(l);
    }

    protected void notifyUpdateListeners() {
      if (listeners == null) return;
      for (UpdateListener l : listeners) {
        l.contextUpdated();
      }
    }

    @NotNull
    public <T extends UserScaleContext> T copy() {
      UserScaleContext ctx = createIdentity();
      ctx.updateAll(this);
      //noinspection unchecked
      return (T)ctx;
    }

    @Override
    public String toString() {
      return usrScale + ", " + objScale + ", " + pixScale;
    }

    /**
     * A cache for the last usage of a data object matching a scale context.
     *
     * @param <D> the data type
     * @param <S> the context type
     */
    public static class Cache<D, S extends UserScaleContext> {
      private final Function<? super S, ? extends D> myDataProvider;
      private final AtomicReference<Pair<Double, D>> myData = new AtomicReference<>(null);

      /**
       * @param dataProvider provides a data object matching the passed scale context
       */
      public Cache(@NotNull Function<? super S, ? extends D> dataProvider) {
        myDataProvider = dataProvider;
      }

      /**
       * Retunrs the data object from the cache if it matches the {@code ctx},
       * otherwise provides the new data via the provider and caches it.
       */
      @Nullable
      public D getOrProvide(@NotNull S ctx) {
        Pair<Double, D> data = myData.get();
        double scale = ctx.getScale(PIX_SCALE);
        if (data == null || Double.compare(scale, data.first) != 0) {
          myData.set(data = Pair.create(scale, myDataProvider.apply(ctx)));
        }
        return data.second;
      }

      /**
       * Clears the cache.
       */
      public void clear() {
        myData.set(null);
      }
    }
  }

  /**
   * Extends {@link UserScaleContext} with the system scale, and is thus used for raster-based painting.
   * The context is created via a context provider. If the provider is {@link Component}, the context's
   * system scale can be updated via a call to {@link #update()}, reflecting the current component's
   * system scale (which may change as the component moves b/w devices).
   *
   * @see ScaleContextAware
   */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static class ScaleContext extends UserScaleContext {
    protected Scale sysScale = SYS_SCALE.of(JBUI.sysScale());

    @Nullable
    protected WeakReference<Component> compRef;

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
    @NotNull
    public static ScaleContext createIdentity() {
      return create(USR_SCALE.of(1), SYS_SCALE.of(1));
    }

    /**
     * Creates a context from the provided {@code ctx}.
     */
    @NotNull
    public static ScaleContext create(@Nullable UserScaleContext ctx) {
      ScaleContext c = create();
      c.update(ctx);
      return c;
    }

    /**
     * Creates a context based on the comp's system scale and sticks to it via the {@link #update()} method.
     */
    @NotNull
    public static ScaleContext create(@Nullable Component comp) {
      final ScaleContext ctx = new ScaleContext(SYS_SCALE.of(JBUI.sysScale(comp)));
      if (comp != null) ctx.compRef = new WeakReference<>(comp);
      return ctx;
    }

    /**
     * Creates a context based on the gc's system scale
     */
    @NotNull
    public static ScaleContext create(@Nullable GraphicsConfiguration gc) {
      return new ScaleContext(SYS_SCALE.of(JBUI.sysScale(gc)));
    }

    /**
     * Creates a context based on the g's system scale
     */
    @NotNull
    public static ScaleContext create(Graphics2D g) {
      return new ScaleContext(SYS_SCALE.of(JBUI.sysScale(g)));
    }

    /**
     * Creates a context with the provided scale
     */
    @NotNull
    public static ScaleContext create(@NotNull Scale scale) {
      return new ScaleContext(scale);
    }

    /**
     * Creates a context with the provided scale factors
     */
    @NotNull
    public static ScaleContext create(@NotNull Scale... scales) {
      ScaleContext ctx = create();
      for (Scale s : scales) ctx.setScale(s);
      return ctx;
    }

    /**
     * Creates a default context with the default screen scale and the current user scale
     */
    @NotNull
    public static ScaleContext create() {
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

    /**
     * {@inheritDoc}
     */
    @Override
    public double getScale(@NotNull DerivedScaleType type) {
      switch (type) {
        case DEV_SCALE: return UIUtil.isJreHiDPIEnabled() ? sysScale.value : 1;
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
      boolean updated = setScale(USR_SCALE.of(JBUI.scale(1f)));
      if (compRef != null) {
        Component comp = compRef.get();
        if (comp != null) updated = setScale(SYS_SCALE.of(JBUI.sysScale(comp))) || updated;
      }
      return onUpdated(updated);
    }

    /**
     * {@inheritDoc}
     * Also includes the system scale.
     */
    @Override
    public boolean setScale(@NotNull Scale scale) {
      if (isScaleOverridden(scale)) return false;

      if (scale.type == SYS_SCALE) {
        boolean updated = !sysScale.equals(scale);
        sysScale = scale;
        return onUpdated(updated);
      }
      return super.setScale(scale);
    }

    @Override
    protected <T extends UserScaleContext> boolean updateAll(@NotNull T ctx) {
      boolean updated = super.updateAll(ctx);
      if (!(ctx instanceof ScaleContext)) return updated;
      ScaleContext context = (ScaleContext)ctx;

      if (compRef != null) compRef.clear();
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

    @NotNull
    @Override
    public <T extends UserScaleContext> T copy() {
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

  /**
   * Provides ScaleContext awareness of a UI object.
   *
   * @see ScaleContextSupport
   */
  public interface ScaleContextAware {
    /**
     * @return the scale context
     */
    @NotNull
    UserScaleContext getScaleContext();

    /**
     * Updates the current context with the state of the provided context.
     * If {@code ctx} is null, then updates the current context via {@link ScaleContext#update()}
     * and returns the result.
     *
     * @param ctx the new scale context
     * @return whether any of the scale factors has been updated
     */
    boolean updateScaleContext(@Nullable UserScaleContext ctx);

    /**
     * @return the scale of the provided type from the context
     */
    double getScale(@NotNull ScaleType type);

    /**
     * @return the scale of the provided type from the context
     */
    double getScale(@NotNull DerivedScaleType type);

    /**
     * Sets the new scale in the context
     *
     * @return whether the provided scale updated the current value
     */
    boolean setScale(@NotNull Scale scale);
  }

  private static abstract class AbstractScaleContextAware<T extends UserScaleContext> implements ScaleContextAware {
    @NotNull
    private final T myScaleContext;

    AbstractScaleContextAware(@NotNull T ctx) {
      myScaleContext = ctx;
    }

    @NotNull
    @Override
    public T getScaleContext() {
      return myScaleContext;
    }

    @Override
    public boolean updateScaleContext(@Nullable UserScaleContext ctx) {
      return myScaleContext.update(ctx);
    }

    @Override
    public double getScale(@NotNull ScaleType type) {
      return getScaleContext().getScale(type);
    }

    @Override
    public double getScale(@NotNull DerivedScaleType type) {
      return getScaleContext().getScale(type);
    }

    @Override
    public boolean setScale(@NotNull Scale scale) {
      return getScaleContext().setScale(scale);
    }
  }

  /**
   * Support for {@link UserScaleContext} (device scale independent).
   */
  public static class UserScaleContextSupport extends AbstractScaleContextAware<UserScaleContext> {
    public UserScaleContextSupport() {
      super(UserScaleContext.create());
    }
  }

  /**
   * Support for {@link ScaleContext} (device scale dependent).
   */
  public static class ScaleContextSupport extends AbstractScaleContextAware<ScaleContext> {
    public ScaleContextSupport() {
      super(ScaleContext.create());
    }
  }
}
