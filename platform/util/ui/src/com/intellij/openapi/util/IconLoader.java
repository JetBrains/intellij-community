// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.reference.SoftReference;
import com.intellij.ui.Gray;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.RetrievableIcon;
import com.intellij.ui.icons.*;
import com.intellij.ui.scale.*;
import com.intellij.util.*;
import com.intellij.util.containers.FixedHashMap;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageFilter;
import java.awt.image.RGBImageFilter;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.ui.paint.PaintUtil.RoundingMode.ROUND;
import static com.intellij.ui.scale.DerivedScaleType.DEV_SCALE;
import static com.intellij.ui.scale.ScaleType.*;

/**
 * Provides access to icons used in the UI.
 * <p/>
 * Please see <a href="http://www.jetbrains.org/intellij/sdk/docs/reference_guide/work_with_icons_and_images.html">Working with Icons and Images</a>
 * about supported formats, organization, and accessing icons in plugins.
 *
 * @see com.intellij.util.IconUtil
 */
public final class IconLoader {
  private static final Logger LOG = Logger.getInstance(IconLoader.class);

  // the key: URL or Pair(path, classLoader)
  private static final ConcurrentMap<Object, CachedImageIcon> iconCache = new ConcurrentHashMap<>(100, 0.9f, 2);
  /**
   * This cache contains mapping between icons and disabled icons.
   */
  private static final Cache<Icon, Icon> iconToDisabledIcon = Caffeine.newBuilder().weakKeys().build();

  private static volatile boolean STRICT_GLOBAL;

  private static final ThreadLocal<Boolean> STRICT_LOCAL = new ThreadLocal<>() {
    @Override
    protected Boolean initialValue() {
      return false;
    }

    @Override
    public Boolean get() {
      return STRICT_GLOBAL || super.get();
    }
  };

  private static final AtomicReference<IconTransform> pathTransform = new AtomicReference<>(
    new IconTransform(StartupUiUtil.isUnderDarcula(), new IconPathPatcher[]{new DeprecatedDuplicatesIconPathPatcher()}, null)
  );
  private static final AtomicInteger pathTransformGlobalModCount = new AtomicInteger();

  @SuppressWarnings("UndesirableClassUsage")
  private static final ImageIcon EMPTY_ICON = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)) {
    @Override
    @NonNls
    public String toString() {
      return "Empty icon " + super.toString();
    }
  };

  private static boolean ourIsActivated;

  private IconLoader() { }

  public static <T> T performStrictly(@NotNull Supplier<? extends T> computable) {
    STRICT_LOCAL.set(true);
    try {
      return computable.get();
    }
    finally {
      STRICT_LOCAL.set(false);
    }
  }

  public static void setStrictGlobally(boolean strict) {
    STRICT_GLOBAL = strict;
  }

  private static void updateTransform(@NotNull Function<? super IconTransform, @NotNull IconTransform> updater) {
    IconTransform prev;
    IconTransform next;
    do {
      prev = pathTransform.get();
      next = updater.apply(prev);
    }
    while (!pathTransform.compareAndSet(prev, next));
    pathTransformGlobalModCount.incrementAndGet();

    if (prev != next) {
      iconToDisabledIcon.invalidateAll();
      //clears svg cache
      ImageLoader.clearCache();
    }
  }

  public static void installPathPatcher(@NotNull IconPathPatcher patcher) {
    updateTransform(transform -> transform.withPathPatcher(patcher));
  }

  public static void removePathPatcher(@NotNull IconPathPatcher patcher) {
    updateTransform(transform -> transform.withoutPathPatcher(patcher));
  }

  /**
   * @deprecated use {@link JBImageIcon}
   */
  @Deprecated
  public static @NotNull Icon getIcon(@NotNull Image image) {
    return new JBImageIcon(image);
  }

  public static void setUseDarkIcons(boolean useDarkIcons) {
    updateTransform(transform -> transform.withDark(useDarkIcons));
  }

  public static void setFilter(ImageFilter filter) {
    updateTransform(transform -> transform.withFilter(filter));
  }

  public static void clearCache() {
    // Copy the transform to trigger update of cached icons
    updateTransform(IconTransform::copy);
  }

  /**
   * @deprecated Use {@link #getIcon(String, Class)}
   */
  @Deprecated
  public static @NotNull Icon getIcon(@NonNls @NotNull String path) {
    Class<?> callerClass = ReflectionUtil.getGrandCallerClass();
    assert callerClass != null : path;
    return getIcon(path, callerClass);
  }

  public static @Nullable Icon getReflectiveIcon(@NotNull String path, @NotNull ClassLoader classLoader) {
    try {
      @NonNls String packageName = path.startsWith("AllIcons.") ? "com.intellij.icons." : "icons.";
      Class<?> aClass = Class.forName(packageName + path.substring(0, path.lastIndexOf('.')).replace('.', '$'), true, classLoader);
      Field field = aClass.getField(path.substring(path.lastIndexOf('.') + 1));
      field.setAccessible(true);
      return (Icon)field.get(null);
    }
    catch (Exception e) {
      return null;
    }
  }

  /**
   * Might return null if icon was not found.
   * Use only if you expected null return value, otherwise see {@link IconLoader#getIcon(String)}
   */
  public static @Nullable Icon findIcon(@NonNls @NotNull String path) {
    Class<?> callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) {
      return null;
    }
    return findIcon(path, callerClass);
  }

  /**
   * @deprecated Use {@link #findIcon(String, Class, boolean, boolean)}
   */
  @Deprecated
  public static @Nullable Icon findIcon(@NonNls @NotNull String path, boolean strict) {
    Class<?> callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) {
      return null;
    }
    return findIcon(path, callerClass, callerClass.getClassLoader(), strict ? HandleNotFound.THROW_EXCEPTION : HandleNotFound.IGNORE, false);
  }

  public static @NotNull Icon getIcon(@NotNull String path, @NotNull Class<?> aClass) {
    Icon icon = findIcon(path, aClass, aClass.getClassLoader(), null, true);
    if (icon == null) {
      throw new IllegalStateException("Icon cannot be found in '" + path + "', class='" + aClass.getName() + "'");
    }
    return icon;
  }

  // reflective path is not supported
  // result is not cached
  @SuppressWarnings("DuplicatedCode")
  @ApiStatus.Internal
  public static @NotNull IconLoader.ImageDataLoader loadRasterizedIcon(@NotNull String path, @NotNull Class<?> aClass, long cacheKey, int imageFlags) {
    ClassLoader classLoader = aClass.getClassLoader();
    long startTime = StartUpMeasurer.getCurrentTimeIfEnabled();
    Pair<String, ClassLoader> patchedPath = pathTransform.get().patchPath(path, classLoader);
    String effectivePath = patchedPath == null ? path : patchedPath.first;
    if (patchedPath != null && patchedPath.second != null) {
      classLoader = patchedPath.second;
    }

    ImageDataLoader resolver = new ImageDataResolverImpl(effectivePath, aClass, classLoader, null, false) {
      @Override
      public @Nullable Image loadImage(@Nullable List<ImageFilter> filters, @NotNull ScaleContext scaleContext, boolean isDark) {
        // do not use cache
        int flags = ImageLoader.ALLOW_FLOAT_SCALING;
        if (isDark) {
          flags |= ImageLoader.USE_DARK;
        }
        assert overriddenPath != null;
        assert ownerClass != null;
        return ImageLoader.loadRasterized(overriddenPath, filters, ownerClass, flags, scaleContext, cacheKey == 0, cacheKey, imageFlags);
      }
    };
    if (startTime != -1) {
      IconLoadMeasurer.addFindIcon(startTime);
    }
    return resolver;
  }

  public static void activate() {
    ourIsActivated = true;
  }

  @TestOnly
  public static void deactivate() {
    ourIsActivated = false;
  }

  private static boolean isLoaderDisabled() {
    return !ourIsActivated;
  }

  @Nullable
  public static Icon findLafIcon(@NotNull String key, @NotNull Class<?> aClass, boolean strict) {
    return findIcon(key + ".png", aClass, aClass.getClassLoader(), strict ? HandleNotFound.THROW_EXCEPTION : HandleNotFound.IGNORE, true);
  }

  /**
   * Might return null if icon was not found.
   * Use only if you expected null return value, otherwise see {@link IconLoader#getIcon(String, Class)}
   */
  @Nullable
  public static Icon findIcon(@NotNull String path, @NotNull Class<?> aClass) {
    return findIcon(path, aClass, aClass.getClassLoader(), null, false);
  }

  public static @Nullable Icon findIcon(@NotNull String path, @NotNull Class<?> aClass, boolean deferUrlResolve, boolean strict) {
    return findIcon(path, aClass, aClass.getClassLoader(), strict ? HandleNotFound.THROW_EXCEPTION : HandleNotFound.IGNORE, deferUrlResolve);
  }

  private static boolean isReflectivePath(@NotNull String path) {
    if (path.isEmpty() || path.charAt(0) == '/') {
      return false;
    }

    int dotIndex = path.indexOf('.');
    if (dotIndex < 0) {
      return false;
    }

    int suffixLength = "Icons".length();
    return path.regionMatches(dotIndex - suffixLength, "Icons", 0, suffixLength);
  }

  public static @Nullable Icon findIcon(URL url) {
    return findIcon(url, true);
  }

  public static @Nullable Icon findIcon(@Nullable URL url, boolean useCache) {
    if (url == null) {
      return null;
    }

    if (useCache) {
      return iconCache.computeIfAbsent(url, url1 -> new CachedImageIcon((URL)url1, true));
    }
    else {
      CachedImageIcon icon = iconCache.get(url);
      return icon == null ? null : new CachedImageIcon(url, false);
    }
  }

  @SuppressWarnings("DuplicatedCode")
  private static @Nullable Icon findIcon(@NotNull String originalPath,
                                         @Nullable Class<?> clazz,
                                         @NotNull ClassLoader classLoader,
                                         @Nullable HandleNotFound handleNotFound,
                                         boolean deferUrlResolve) {
    long startTime = StartUpMeasurer.getCurrentTimeIfEnabled();
    Pair<String, ClassLoader> patchedPath = pathTransform.get().patchPath(originalPath, classLoader);
    String path = patchedPath == null ? originalPath : patchedPath.first;
    if (patchedPath != null && patchedPath.second != null) {
      classLoader = patchedPath.second;
    }

    Icon icon;
    if (isReflectivePath(path)) {
      icon = getReflectiveIcon(path, classLoader);
    }
    else {
      Pair<String, Object> key = new Pair<>(originalPath, classLoader);
      CachedImageIcon cachedIcon = iconCache.get(key);
      if (cachedIcon == null) {
        cachedIcon = iconCache.computeIfAbsent(key, k -> {
          @SuppressWarnings("unchecked")
          ClassLoader classLoader1 = (ClassLoader)((Pair<String, Object>)k).getSecond();
          ImageDataResolverImpl resolver = new ImageDataResolverImpl(path, clazz, classLoader1, handleNotFound, /* useCacheOnLoad = */ true);
          if (!deferUrlResolve && resolver.getURL() == null) {
            return null;
          }
          return new CachedImageIcon(originalPath, resolver, null, null);
        });
      }
      else {
        ScaleContext scaleContext = ScaleContext.create();
        if (!cachedIcon.getScaleContext().equals(scaleContext)) {
          // honor scale context as 'iconCache' doesn't do that
          cachedIcon = cachedIcon.copy();
          cachedIcon.updateScaleContext(scaleContext);
        }
      }
      icon = cachedIcon;
    }

    if (startTime != -1) {
      IconLoadMeasurer.addFindIcon(startTime);
    }
    return icon;
  }

  @Nullable
  public static Icon findIcon(@NotNull String path, @NotNull ClassLoader classLoader) {
    return findIcon(path, null, classLoader, HandleNotFound.IGNORE, false);
  }

  @Nullable
  public static Image toImage(@NotNull Icon icon) {
    return toImage(icon, null);
  }

  public static @Nullable Image toImage(@NotNull Icon icon, @Nullable ScaleContext ctx) {
    if (icon instanceof RetrievableIcon) {
      icon = getOrigin((RetrievableIcon)icon);
    }
    if (icon instanceof CachedImageIcon) {
      icon = ((CachedImageIcon)icon).getRealIcon(ctx);
    }

    if (icon instanceof ImageIcon) {
      return ((ImageIcon)icon).getImage();
    }
    else {
      if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0) {
        return null;
      }
      BufferedImage image;
      if (GraphicsEnvironment.isHeadless()) {
        // for testing purpose
        image = ImageUtil.createImage(ctx, icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB, ROUND);
      }
      else {
        if (ctx == null) ctx = ScaleContext.create();
        image = GraphicsEnvironment.getLocalGraphicsEnvironment()
          .getDefaultScreenDevice().getDefaultConfiguration()
          .createCompatibleImage(ROUND.round(ctx.apply(icon.getIconWidth(), DEV_SCALE)),
                                 ROUND.round(ctx.apply(icon.getIconHeight(), DEV_SCALE)),
                                 Transparency.TRANSLUCENT);
        if (StartupUiUtil.isJreHiDPI(ctx)) {
          image = (BufferedImage)ImageUtil.ensureHiDPI(image, ctx, icon.getIconWidth(), icon.getIconHeight());
        }
      }
      Graphics2D g = image.createGraphics();
      try {
        icon.paintIcon(null, g, 0, 0);
      }
      finally {
        g.dispose();
      }
      return image;
    }
  }

  @Contract("null, _, _->null; !null, _, _->!null")
  public static Icon copy(@Nullable Icon icon, @Nullable Component ancestor, boolean deepCopy) {
    if (icon == null) {
      return null;
    }
    if (icon instanceof CopyableIcon) {
      return deepCopy ? ((CopyableIcon)icon).deepCopy() : ((CopyableIcon)icon).copy();
    }

    BufferedImage image = ImageUtil.createImage(ancestor == null ? null : ancestor.getGraphicsConfiguration(),
                                                icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      icon.paintIcon(ancestor, g, 0, 0);
    }
    finally {
      g.dispose();
    }

    return new JBImageIcon(image) {
      final int originalWidth = icon.getIconWidth();
      final int originalHeight = icon.getIconHeight();
      @Override
      public int getIconWidth() {
        return originalWidth;
      }

      @Override
      public int getIconHeight() {
        return originalHeight;
      }
    };
  }

  private static @Nullable ImageIcon checkIcon(@NotNull Image image, @NotNull CachedImageIcon cii) {
    // image wasn't loaded or broken
    if (image.getHeight(null) < 1) {
      return null;
    }

    ImageIcon icon = new JBImageIcon(image);
    if (!isGoodSize(icon)) {
      // # 22481
      LOG.error("Invalid icon: " + cii);
      return EMPTY_ICON;
    }
    return icon;
  }

  public static boolean isGoodSize(@NotNull final Icon icon) {
    return icon.getIconWidth() > 0 && icon.getIconHeight() > 0;
  }

  /**
   * Gets (creates if necessary) disabled icon based on the passed one.
   *
   * @return {@code ImageIcon} constructed from disabled image of passed icon.
   */
  @NotNull
  public static Icon getDisabledIcon(@NotNull Icon icon) {
    return getDisabledIcon(icon, null);
  }

  /**
   * Same as {@link #getDisabledIcon(Icon)} with an ancestor component for HiDPI-awareness.
   */
  public static @NotNull Icon getDisabledIcon(@NotNull Icon icon, @Nullable Component ancestor) {
    if (!ourIsActivated) {
      return icon;
    }

    if (icon instanceof LazyIcon) {
      icon = ((LazyIcon)icon).getOrComputeIcon();
    }
    if (icon instanceof RetrievableIcon) {
      icon = getOrigin((RetrievableIcon)icon);
    }

    return Objects.requireNonNull(iconToDisabledIcon.get(icon, it -> {
      return filterIcon(it, UIUtil::getGrayFilter/* returns laf-aware instance */, ancestor);
    }));
  }

  /**
   * Creates new icon with the filter applied.
   */
  public static @NotNull Icon filterIcon(@NotNull Icon icon,
                                         @NotNull Supplier<? extends RGBImageFilter> filterSupplier,
                                         @Nullable Component ancestor) {
    if (icon instanceof LazyIcon) {
      icon = ((LazyIcon)icon).getOrComputeIcon();
    }

    if (!isGoodSize(icon)) {
      LOG.error(icon); // # 22481
      return EMPTY_ICON;
    }

    if (icon instanceof CachedImageIcon) {
      return ((CachedImageIcon)icon).createWithFilter(filterSupplier);
    }

    double scale;
    ScaleContextSupport ctxSupport = getScaleContextSupport(icon);
    if (ctxSupport == null) {
      scale = JreHiDpiUtil.isJreHiDPI((GraphicsConfiguration)null) ? JBUIScale.sysScale(ancestor) : 1f;
    }
    else {
      scale = JreHiDpiUtil.isJreHiDPI((GraphicsConfiguration)null) ? ctxSupport.getScale(SYS_SCALE) : 1f;
    }
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage image =
      new BufferedImage((int)(scale * icon.getIconWidth()), (int)(scale * icon.getIconHeight()), BufferedImage.TYPE_INT_ARGB);
    final Graphics2D graphics = image.createGraphics();

    graphics.setColor(Gray.TRANSPARENT);
    graphics.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
    graphics.scale(scale, scale);
    icon.paintIcon(LabelHolder.ourFakeComponent, graphics, 0, 0);

    graphics.dispose();

    Image img = ImageUtil.filter(image, filterSupplier.get());
    if (StartupUiUtil.isJreHiDPI(ancestor)) {
      img = RetinaImage.createFrom(img, scale, null);
    }

    return new JBImageIcon(img);
  }

  @NotNull
  public static Icon getTransparentIcon(@NotNull final Icon icon) {
    return getTransparentIcon(icon, 0.5f);
  }

  @NotNull
  public static Icon getTransparentIcon(@NotNull final Icon icon, final float alpha) {
    return new RetrievableIcon() {
      @Override
      @NotNull
      public Icon retrieveIcon() {
        return icon;
      }

      @Override
      public int getIconHeight() {
        return icon.getIconHeight();
      }

      @Override
      public int getIconWidth() {
        return icon.getIconWidth();
      }

      @Override
      public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
        final Graphics2D g2 = (Graphics2D)g;
        final Composite saveComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha));
        icon.paintIcon(c, g2, x, y);
        g2.setComposite(saveComposite);
      }
    };
  }

  /**
   * Gets a snapshot of the icon, immune to changes made by these calls:
   * {@link #setFilter(ImageFilter)}, {@link #setUseDarkIcons(boolean)}
   *
   * @param icon the source icon
   * @return the icon snapshot
   */
  @NotNull
  public static Icon getIconSnapshot(@NotNull Icon icon) {
    if (icon instanceof CachedImageIcon) {
      return ((CachedImageIcon)icon).getRealIcon();
    }
    return icon;
  }

  /**
   *  For internal usage. Converts the icon to 1x scale when applicable.
   */
  @ApiStatus.Internal
  @NotNull
  public static Icon getMenuBarIcon(@NotNull Icon icon, boolean dark) {
    if (icon instanceof RetrievableIcon) {
      icon = getOrigin((RetrievableIcon)icon);
    }
    if (icon instanceof MenuBarIconProvider) {
      return ((MenuBarIconProvider)icon).getMenuBarIcon(dark);
    }
    return icon;
  }

  /**
   * Returns a copy of the provided {@code icon} with darkness set to {@code dark}.
   * The method takes effect on a {@link CachedImageIcon} (or its wrapper) only.
   */
  @NotNull
  public static Icon getDarkIcon(@NotNull Icon icon, boolean dark) {
    if (icon instanceof RetrievableIcon) {
      icon = getOrigin((RetrievableIcon)icon);
    }
    if (icon instanceof DarkIconProvider) {
      return ((DarkIconProvider)icon).getDarkIcon(dark);
    }
    return icon;
  }

  public static void detachClassLoader(@NotNull ClassLoader classLoader) {
    iconCache.entrySet().removeIf(entry -> {
      CachedImageIcon icon = entry.getValue();
      icon.detachClassLoader(classLoader);
      Object key = entry.getKey();
      return key instanceof Pair && ((Pair<?, ?>)key).second == classLoader;
    });

    iconToDisabledIcon.asMap().keySet().removeIf(icon -> {
      return icon instanceof CachedImageIcon && ((CachedImageIcon)icon).detachClassLoader(classLoader);
    });
  }

  @ApiStatus.Internal
  public static class CachedImageIcon extends ScaleContextSupport implements CopyableIcon, ScalableIcon, DarkIconProvider, MenuBarIconProvider {
    @Nullable private final String originalPath;
    @Nullable private volatile IconLoader.ImageDataLoader resolver;
    @Nullable("when not overridden") private final Boolean isDarkOverridden;
    @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
    private int pathTransformModCount = -1;

    @Nullable private final Supplier<? extends RGBImageFilter> localFilterSupplier;
    private final ScaledIconCache scaledIconCache = new ScaledIconCache(this);
    private final ScaledIconCache selectedIconCache = new ScaledIconCache(this);

    private volatile CachedImageIcon darkVariant;

    private final Object lock = new Object();
    // ImageIcon (if small icon) or SoftReference<ImageIcon> (if large icon)
    private volatile @Nullable Object realIcon;

    public CachedImageIcon(@NotNull URL url) {
      this(url, true);
    }

    CachedImageIcon(@NotNull URL url, boolean useCacheOnLoad) {
      this(null, new ImageDataResolverImpl(url, null, useCacheOnLoad), null, null);

      // if url is explicitly specified, it means that path should be not transformed
      pathTransformModCount = pathTransformGlobalModCount.get();
    }

    protected CachedImageIcon(@Nullable String originalPath,
                              @Nullable IconLoader.ImageDataLoader resolver,
                              @Nullable Boolean darkOverridden,
                              @Nullable Supplier<? extends RGBImageFilter> localFilterSupplier) {
      this.originalPath = originalPath;
      this.resolver = resolver;
      isDarkOverridden = darkOverridden;
      this.localFilterSupplier = localFilterSupplier;

      // For instance, ShadowPainter updates the context from outside.
      getScaleContext().addUpdateListener(new UserScaleContext.UpdateListener() {
        @Override
        public void contextUpdated() {
          realIcon = null;
        }
      });
    }

    private static @Nullable ImageIcon unwrapIcon(Object icon) {
      if (icon == null) {
        return null;
      }
      else if (icon instanceof Reference) {
        //noinspection unchecked
        return ((Reference<ImageIcon>)icon).get();
      }
      else {
        return (ImageIcon)icon;
      }
    }

    @Override
    public final void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2d = g instanceof Graphics2D ? (Graphics2D)g : null;
      ScaleContext scaleContext = ScaleContext.create(g2d);
      if (SVGLoader.isSelectionContext()) {
        ImageIcon result = null;
        synchronized (lock) {
          ImageIcon icon = selectedIconCache.getOrScaleIcon(1f);
          if (icon != null) {
            result = icon;
          }
        }
        if (result == null) {
          result = EMPTY_ICON;
        }
        result.paintIcon(c, g, x, y);
      }
      else {
        getRealIcon(scaleContext).paintIcon(c, g, x, y);
      }
    }

    @Override
    public int getIconWidth() {
      return getRealIcon(null).getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return getRealIcon(null).getIconHeight();
    }

    @Override
    public float getScale() {
      return 1f;
    }

    @ApiStatus.Internal
    public final @NotNull ImageIcon getRealIcon() {
      return getRealIcon(null);
    }

    @TestOnly
    public final @Nullable ImageIcon doGetRealIcon() {
      return unwrapIcon(realIcon);
    }

    public final @Nullable String getOriginalPath() {
      return originalPath;
    }

    private @NotNull ImageIcon getRealIcon(@Nullable ScaleContext context) {
      Object realIcon = this.realIcon;
      ImageDataLoader resolver = this.resolver;
      if (resolver == null) {
        return EMPTY_ICON;
      }

      if (pathTransformGlobalModCount.get() != pathTransformModCount) {
        if (isLoaderDisabled()) {
          return EMPTY_ICON;
        }

        synchronized (lock) {
          resolver = this.resolver;
          if (resolver == null) {
            return EMPTY_ICON;
          }

          realIcon = this.realIcon;
          if (pathTransformGlobalModCount.get() != pathTransformModCount) {
            pathTransformModCount = pathTransformGlobalModCount.get();
            realIcon = null;
            this.realIcon = null;
            scaledIconCache.clear();
            selectedIconCache.clear();
            if (originalPath != null) {
              resolver = resolver.patch(originalPath, pathTransform.get());
              if (resolver != null) {
                this.resolver = resolver;
              }
            }
          }
        }
      }

      synchronized (lock) {
        if (!updateScaleContext(context)) {
          // try returning the current icon as the context is up-to-date
          ImageIcon icon = unwrapIcon(realIcon);
          if (icon != null) {
            return icon;
          }
        }

        ImageIcon icon = scaledIconCache.getOrScaleIcon(1f);
        if (icon != null) {
          this.realIcon = icon.getIconWidth() < 50 && icon.getIconHeight() < 50 ? icon : new SoftReference<>(icon);
          return icon;
        }
      }
      return EMPTY_ICON;
    }

    @Override
    public final String toString() {
      ImageDataLoader resolver = this.resolver;
      if (resolver != null) {
        return resolver.toString();
      }
      return originalPath != null ? originalPath : "unknown path";
    }

    @Override
    public final @NotNull Icon scale(float scale) {
      if (scale == 1f) {
        return this;
      }

      // force state update & cache reset
      getRealIcon();

      Icon icon = scaledIconCache.getOrScaleIcon(scale);
      return icon == null ? this : icon;
    }

    @Override
    public final @NotNull Icon getDarkIcon(boolean isDark) {
      ImageDataLoader resolver = this.resolver;
      if (resolver == null) {
        return EMPTY_ICON;
      }

      CachedImageIcon result = darkVariant;
      if (result == null) {
        synchronized (lock) {
          result = darkVariant;
          if (result == null) {
            result = new CachedImageIcon(originalPath, resolver, isDark, localFilterSupplier);
            darkVariant = result;
          }
        }
      }
      return result;
    }

    @Override
    public final @NotNull Icon getMenuBarIcon(boolean isDark) {
      boolean useMRI = MultiResolutionImageProvider.isMultiResolutionImageAvailable() && SystemInfo.isMac;
      ScaleContext ctx = useMRI ? ScaleContext.create() : ScaleContext.createIdentity();
      ctx.setScale(USR_SCALE.of(1));
      Image img = loadFromUrl(ctx, isDark);
      if (useMRI) {
        img = MultiResolutionImageProvider.convertFromJBImage(img);
      }
      if (img != null) {
        return new ImageIcon(img);
      }
      return this;
    }

    @Override
    public final @NotNull CachedImageIcon copy() {
      CachedImageIcon result = new CachedImageIcon(originalPath, resolver, isDarkOverridden, localFilterSupplier);
      result.pathTransformModCount = pathTransformModCount;
      return result;
    }

    private @NotNull Icon createWithFilter(@NotNull Supplier<? extends RGBImageFilter> filterSupplier) {
      ImageDataLoader resolver = this.resolver;
      if (resolver == null) {
        return EMPTY_ICON;
      }
      return new CachedImageIcon(originalPath, resolver, isDarkOverridden, filterSupplier);
    }

    private boolean isDark() {
      return isDarkOverridden == null ? pathTransform.get().isDark() : isDarkOverridden;
    }

    private @Nullable List<ImageFilter> getFilters() {
      ImageFilter global = pathTransform.get().getFilter();
      ImageFilter local = localFilterSupplier == null ? null : localFilterSupplier.get();
      if (global != null && local != null) {
        return Arrays.asList(global, local);
      }
      else if (global != null) {
        return Collections.singletonList(global);
      }
      else {
        return local == null ? null : Collections.singletonList(local);
      }
    }

    public final @Nullable URL getURL() {
      ImageDataLoader resolver = this.resolver;
      return resolver == null ? null : resolver.getURL();
    }

    private @Nullable Image loadFromUrl(@NotNull ScaleContext scaleContext, boolean isDark) {
      long start = StartUpMeasurer.getCurrentTimeIfEnabled();

      ImageDataLoader resolver = this.resolver;
      if (resolver == null) {
        return null;
      }

      Image image = resolver.loadImage(getFilters(), scaleContext, isDark);
      if (start != -1) {
        IconLoadMeasurer.addFindIconLoad(start);
      }
      return image;
    }

    final boolean detachClassLoader(@NotNull ClassLoader loader) {
      ImageDataLoader resolver = this.resolver;
      //noinspection DuplicatedCode
      if (resolver == null) {
        return true;
      }

      synchronized (lock) {
        resolver = this.resolver;
        if (resolver == null) {
          return true;
        }

        if (!resolver.isMyClassLoader(loader)) {
          return false;
        }

        this.resolver = null;
        scaledIconCache.clear();
        selectedIconCache.clear();

        CachedImageIcon darkVariant = this.darkVariant;
        if (darkVariant != null) {
          this.darkVariant = null;
          darkVariant.detachClassLoader(loader);
        }

        return true;
      }
    }
  }

  private static final class ScaledIconCache {
    private static final int SCALED_ICONS_CACHE_LIMIT = 5;

    private final CachedImageIcon host;
    private final Map<Long, SoftReference<ImageIcon>> cache = Collections.synchronizedMap(new FixedHashMap<>(SCALED_ICONS_CACHE_LIMIT));

    private ScaledIconCache(@NotNull CachedImageIcon host) {
      this.host = host;
    }

    private static long key(@NotNull ScaleContext context) {
      return (((long)Float.floatToIntBits((float)context.getScale(DerivedScaleType.EFF_USR_SCALE))) << 32) |
             ((long)Float.floatToIntBits((float)context.getScale(SYS_SCALE)) & 0xffffffffL);
    }

    /**
     * Retrieves the orig icon scaled by the provided scale.
     */
    ImageIcon getOrScaleIcon(float scale) {
      ScaleContext scaleContext = host.getScaleContext();
      if (scale != 1) {
        scaleContext = scaleContext.copy();
        scaleContext.setScale(OBJ_SCALE.of(scale));
      }

      long cacheKey = key(scaleContext);
      ImageIcon icon = SoftReference.dereference(cache.get(cacheKey));
      if (icon != null && !SVGLoader.isSelectionContext()) {
        return icon;
      }

      Image image = host.loadFromUrl(scaleContext, host.isDark());
      if (image == null) {
        return null;
      }

      icon = checkIcon(image, host);
      if (icon != null && !ImageLoader.isIconTooLargeForCache(icon)) {
        cache.put(cacheKey, new SoftReference<>(icon));
      }
      return icon;
    }

    public void clear() {
      cache.clear();
    }
  }

  enum HandleNotFound {
    THROW_EXCEPTION {
      @Override
      void handle(@NotNull String msg) {
        throw new RuntimeException(msg);
      }
    },
    LOG_ERROR {
      @Override
      void handle(@NotNull String msg) {
        LOG.error(msg);
      }
    },
    IGNORE;

    void handle(@NotNull String msg) throws RuntimeException {}
  }

  @ApiStatus.Internal
  public interface ImageDataLoader {
    @Nullable Image loadImage(@Nullable List<ImageFilter> filters, @NotNull ScaleContext scaleContext, boolean isDark);

    @Nullable URL getURL();

    @Nullable IconLoader.ImageDataLoader patch(@NotNull String originalPath, @NotNull IconTransform transform);

    boolean isMyClassLoader(@NotNull ClassLoader loader);
  }

  /**
   * Used to defer URL resolve.
   */
  private static class ImageDataResolverImpl implements ImageDataLoader {
    private static final URL UNRESOLVED_URL;

    static {
      try {
        UNRESOLVED_URL = new URL("file:///unresolved");
      }
      catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }

    @Nullable protected final Class<?> ownerClass;
    @Nullable protected final ClassLoader classLoader;
    @Nullable protected final String overriddenPath;
    @NotNull private final HandleNotFound handleNotFound;

    private volatile URL url;

    private final boolean useCacheOnLoad;

    ImageDataResolverImpl(@NotNull URL url, @Nullable ClassLoader classLoader, boolean useCacheOnLoad) {
      ownerClass = null;
      overriddenPath = null;
      this.classLoader = classLoader;
      this.url = url;
      handleNotFound = HandleNotFound.IGNORE;
      this.useCacheOnLoad = useCacheOnLoad;
    }

    ImageDataResolverImpl(@NotNull URL url, @NotNull String path, @Nullable ClassLoader classLoader, boolean useCacheOnLoad) {
      ownerClass = null;
      overriddenPath = path;
      this.classLoader = classLoader;
      this.url = url;
      handleNotFound = HandleNotFound.IGNORE;
      this.useCacheOnLoad = useCacheOnLoad;
    }

    ImageDataResolverImpl(@NotNull String path,
                          @Nullable Class<?> clazz,
                          @Nullable ClassLoader classLoader,
                          @Nullable HandleNotFound handleNotFound,
                          boolean useCacheOnLoad) {
      overriddenPath = path;
      ownerClass = clazz;
      this.classLoader = classLoader;
      if (handleNotFound == null) {
        handleNotFound = STRICT_LOCAL.get() ? HandleNotFound.THROW_EXCEPTION : HandleNotFound.IGNORE;
      }
      this.handleNotFound = handleNotFound;
      this.useCacheOnLoad = useCacheOnLoad;
      url = UNRESOLVED_URL;
    }

    @Override
    public @Nullable Image loadImage(@Nullable List<ImageFilter> filters, @NotNull ScaleContext scaleContext, boolean isDark) {
      int flags = ImageLoader.USE_SVG | ImageLoader.ALLOW_FLOAT_SCALING;
      if (useCacheOnLoad) {
        flags |= ImageLoader.USE_CACHE;
      }
      if (isDark) {
        flags |= ImageLoader.USE_DARK;
      }

      if (ownerClass != null && overriddenPath != null) {
        return ImageLoader.loadFromUrl(overriddenPath, ownerClass, flags, filters, scaleContext);
      }
      else {
        URL url = getURL();
        return url == null ? null : ImageLoader.loadFromUrl(url.toString(), null, flags, filters, scaleContext);
      }
    }

    /**
     * Resolves the URL if it's not yet resolved.
     */
    public final void resolve() {
      getURL();
    }

    private static @Nullable URL doResolve(@Nullable String overriddenPath,
                                           @Nullable ClassLoader classLoader,
                                           @Nullable Class<?> ownerClass,
                                           @NotNull HandleNotFound handleNotFound) {
      String path = overriddenPath;
      URL url = null;
      if (path != null) {
        if (classLoader != null) {
          // paths in ClassLoader getResource must not start with "/"
          path = path.charAt(0) == '/' ? path.substring(1) : path;
          url = findUrl(path, classLoader::getResource);
        }
        if (url == null && ownerClass != null) {
          // some plugins use findIcon("icon.png",IconContainer.class)
          url = findUrl(path, ownerClass::getResource);
        }
      }
      if (url == null) {
        handleNotFound.handle("Can't find icon in '" + path + "' near " + classLoader);
      }
      return url;
    }

    @Override
    public final @Nullable URL getURL() {
      URL result = this.url;
      if (result == UNRESOLVED_URL) {
        result = null;
        try {
          result = doResolve(overriddenPath, classLoader, ownerClass, handleNotFound);
        }
        finally {
          this.url = result;
        }
      }
      return result;
    }

    @Override
    public final @Nullable IconLoader.ImageDataLoader patch(@NotNull String originalPath, @NotNull IconTransform transform) {
      Pair<String, ClassLoader> patchedPath = transform.patchPath(originalPath, classLoader);
      if (patchedPath == null) {
        return null;
      }

      ClassLoader classLoader = patchedPath.second == null ? this.classLoader : patchedPath.second;
      String path = patchedPath.first;
      if (classLoader != null && path != null && path.startsWith("/")) {
        ImageDataResolverImpl resolver = new ImageDataResolverImpl(path.substring(1), null, classLoader, handleNotFound, useCacheOnLoad);
        resolver.resolve();
        return resolver;
      }

      // This use case for temp themes only. Here we want immediately replace existing icon to a local one
      if (path != null && path.startsWith("file:/")) {
        try {
          ImageDataResolverImpl resolver = new ImageDataResolverImpl(new URL(path), path.substring(1), classLoader, useCacheOnLoad);
          resolver.resolve();
          return resolver;
        }
        catch (MalformedURLException ignore) {
        }
      }

      return null;
    }

    @SuppressWarnings("DuplicateExpressions")
    private static @Nullable URL findUrl(@NotNull String path, @NotNull Function<? super String, URL> urlProvider) {
      URL url = urlProvider.apply(path);
      if (url != null) {
        return url;
      }

      // Find either PNG or SVG icon. The icon will then be wrapped into CachedImageIcon
      // which will load proper icon version depending on the context - UI theme, DPI.
      // SVG version, when present, has more priority than PNG.
      // See for details: com.intellij.util.ImageLoader.ImageDescList#create
      if (path.endsWith(".png")) {
        path = path.substring(0, path.length() - 4) + ".svg";
      }
      else if (path.endsWith(".svg")) {
        path = path.substring(0, path.length() - 4) + ".png";
      }
      else {
        LOG.debug("unexpected path: ", path);
      }
      return urlProvider.apply(path);
    }

    @Override
    public final boolean isMyClassLoader(@NotNull ClassLoader loader) {
      return classLoader == loader;
    }

    @Override
    public final String toString() {
      return "UrlResolver{" +
             "ownerClass=" + (ownerClass == null ? "null" : ownerClass.getName()) +
             ", classLoader=" + classLoader +
             ", overriddenPath='" + overriddenPath + '\'' +
             ", url=" + url +
             ", useCacheOnLoad=" + useCacheOnLoad +
             '}';
    }
  }

  @NotNull
  public static Icon createLazy(@NotNull Supplier<? extends @NotNull Icon> producer) {
    return new LazyIcon() {
      @Override
      @NotNull
      protected Icon compute() {
        return producer.get();
      }
    };
  }

  @ApiStatus.Internal
  public abstract static class LazyIcon extends ScaleContextSupport implements CopyableIcon, RetrievableIcon {
    private boolean myWasComputed;
    private volatile Icon myIcon;
    private int myTransformModCount = pathTransformGlobalModCount.get();

    /**
     * @deprecated Use {@link IconLoader#createLazy}.
     */
    @SuppressWarnings({"RedundantNoArgConstructor", "DeprecatedIsStillUsed"})
    @Deprecated
    public LazyIcon() {
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      if (updateScaleContext(ScaleContext.create((Graphics2D)g))) {
        myIcon = null;
      }
      final Icon icon = getOrComputeIcon();
      icon.paintIcon(c, g, x, y);
    }

    @Override
    public int getIconWidth() {
      final Icon icon = getOrComputeIcon();
      return icon.getIconWidth();
    }

    @Override
    public int getIconHeight() {
      final Icon icon = getOrComputeIcon();
      return icon.getIconHeight();
    }

    @NotNull
    final synchronized Icon getOrComputeIcon() {
      Icon icon = myIcon;
      int newTransformModCount = pathTransformGlobalModCount.get();
      if (icon == null || !myWasComputed || myTransformModCount != newTransformModCount) {
        myTransformModCount = newTransformModCount;
        myWasComputed = true;
        try {
          icon = compute();
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Throwable e) {
          LOG.error("Cannot compute icon", e);
          icon = AllIcons.Actions.Stub;
        }

        myIcon = icon;
      }

      return icon;
    }

    public final void load() {
      getIconWidth();
    }

    @NotNull
    protected abstract Icon compute();

    @NotNull
    @Override
    public Icon retrieveIcon() {
      return getOrComputeIcon();
    }

    @NotNull
    @Override
    public Icon copy() {
      return IconLoader.copy(getOrComputeIcon(), null, false);
    }
  }

  @NotNull
  private static Icon getOrigin(@NotNull RetrievableIcon icon) {
    final int maxDeep = 10;
    Icon origin = icon.retrieveIcon();
    int level = 0;
    while (origin instanceof RetrievableIcon && level < maxDeep) {
      ++level;
      origin = ((RetrievableIcon)origin).retrieveIcon();
    }
    if (origin instanceof RetrievableIcon) {
      LOG.error("can't calculate origin icon (too deep in hierarchy), src: " + icon);
    }
    return origin;
  }

  /**
   * Returns {@link ScaleContextSupport} which best represents this icon taking into account its compound structure,
   * or null when not applicable.
   */
  @Nullable
  private static ScaleContextSupport getScaleContextSupport(@NotNull Icon icon) {
    if (icon instanceof ScaleContextSupport) {
      return (ScaleContextSupport)icon;
    }
    if (icon instanceof RetrievableIcon) {
      return getScaleContextSupport(((RetrievableIcon)icon).retrieveIcon());
    }
    if (icon instanceof CompositeIcon) {
      return getScaleContextSupport(Objects.requireNonNull(((CompositeIcon)icon).getIcon(0)));
    }
    return null;
  }

  private static class LabelHolder {
    /**
     * To get disabled icon with paint it into the image. Some icons require
     * not null component to paint.
     */
    private static final JComponent ourFakeComponent = new JComponent() {
    };
  }

  /**
   * Immutable representation of a global transformation applied to all icons
   */
  private static final class IconTransform {
    private final boolean myDark;
    private final IconPathPatcher @NotNull [] myPatchers;
    private final @Nullable ImageFilter myFilter;

    private IconTransform(boolean dark, IconPathPatcher @NotNull [] patchers, @Nullable ImageFilter filter) {
      myDark = dark;
      myPatchers = patchers;
      myFilter = filter;
    }

    boolean isDark() {
      return myDark;
    }

    @Nullable
    ImageFilter getFilter() {
      return myFilter;
    }

    @NotNull
    IconTransform withPathPatcher(@NotNull IconPathPatcher patcher) {
      return new IconTransform(myDark, ArrayUtil.append(myPatchers, patcher), myFilter);
    }

    @NotNull
    IconTransform withoutPathPatcher(@NotNull IconPathPatcher patcher) {
      IconPathPatcher[] newPatchers = ArrayUtil.remove(myPatchers, patcher);
      return newPatchers == myPatchers ? this : new IconTransform(myDark, newPatchers, myFilter);
    }

    @NotNull
    public IconTransform withFilter(ImageFilter filter) {
      return filter == myFilter ? this : new IconTransform(myDark, myPatchers, filter);
    }

    @NotNull
    IconTransform withDark(boolean dark) {
      return dark == myDark ? this : new IconTransform(dark, myPatchers, myFilter);
    }

    public @Nullable Pair<String, ClassLoader> patchPath(@NotNull String path, @Nullable ClassLoader classLoader) {
      for (IconPathPatcher patcher : myPatchers) {
        String newPath = patcher.patchPath(path, classLoader);
        if (newPath == null) {
          continue;
        }

        LOG.debug("replace '" + path + "' with '" + newPath + "'");
        ClassLoader contextClassLoader = patcher.getContextClassLoader(path, classLoader);
        if (contextClassLoader == null) {
          //noinspection deprecation
          Class<?> contextClass = patcher.getContextClass(path);
          if (contextClass != null) {
            contextClassLoader = contextClass.getClassLoader();
          }
        }
        return new Pair<>(newPath, contextClassLoader);
      }
      return null;
    }

    public @NotNull IconTransform copy() {
      return new IconTransform(myDark, myPatchers, myFilter);
    }
  }
}
