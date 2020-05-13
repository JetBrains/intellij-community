// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.IconLoader.CachedImageIcon.HandleNotFound;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.RetrievableIcon;
import com.intellij.ui.icons.*;
import com.intellij.ui.scale.DerivedScaleType;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleContextSupport;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FixedHashMap;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageFilter;
import java.awt.image.RGBImageFilter;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
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
 * @see IconUtil
 */
public final class IconLoader {
  private static final Logger LOG = Logger.getInstance(IconLoader.class);

  private static final String ICON_CACHE_URL_KEY = "ICON_CACHE_URL_KEY";
  // the key: Pair(ICON_CACHE_URL_KEY, url) or Pair(path, classLoader)
  private static final ConcurrentMap<Pair<String, Object>, CachedImageIcon> ourIconsCache =
    ContainerUtil.newConcurrentMap(100, 0.9f, 2);
  /**
   * This cache contains mapping between icons and disabled icons.
   */
  private static final ConcurrentMap<Icon, Icon> ourIcon2DisabledIcon = ContainerUtil.createConcurrentWeakMap(200, 0.75f, Math.min(Runtime.getRuntime().availableProcessors(), 4), ContainerUtil.canonicalStrategy());

  private static volatile boolean STRICT_GLOBAL;

  private static final ThreadLocal<Boolean> STRICT_LOCAL = new ThreadLocal<Boolean>() {
    @Override
    protected Boolean initialValue() {
      return false;
    }

    @Override
    public Boolean get() {
      if (STRICT_GLOBAL) return true;
      return super.get();
    }
  };

  private static final AtomicReference<IconTransform> ourTransform = new AtomicReference<>(IconTransform.getDefault());
  private static final AtomicInteger ourTransformModCount = new AtomicInteger(0);

  static {
    installPathPatcher(new DeprecatedDuplicatesIconPathPatcher());
  }

  private static final ImageIcon EMPTY_ICON = new ImageIcon(ImageUtil.createImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)) {
    @Override
    @NonNls
    public String toString() {
      return "Empty icon " + super.toString();
    }
  };

  private static boolean ourIsActivated;

  private IconLoader() { }

  public static <T, E extends Throwable> T performStrictly(ThrowableComputable<T, E> computable) throws E {
    STRICT_LOCAL.set(true);
    try {
      return computable.compute();
    } finally {
      STRICT_LOCAL.set(false);
    }
  }

  public static void setStrictGlobally(boolean strict) {
    STRICT_GLOBAL = strict;
  }

  private static void updateTransform(@NotNull Function<? super IconTransform, IconTransform> updater) {
    IconTransform prev;
    IconTransform next;
    do {
      prev = ourTransform.get();
      next = updater.apply(prev);
    }
    while (!ourTransform.compareAndSet(prev, next));
    ourTransformModCount.incrementAndGet();

    if (prev != next) {
      ourIcon2DisabledIcon.clear();
      //clears svg cache
      ImageDescriptor.clearCache();
    }
  }

  public static void installPathPatcher(@NotNull final IconPathPatcher patcher) {
    updateTransform(transform -> transform.withPathPatcher(patcher));
  }

  public static void removePathPatcher(@NotNull final IconPathPatcher patcher) {
    updateTransform(transform -> transform.withoutPathPatcher(patcher));
  }

  /**
   * @deprecated use {@link JBImageIcon}
   */
  @Deprecated
  @NotNull
  public static Icon getIcon(@NotNull final Image image) {
    return new JBImageIcon(image);
  }

  public static void setUseDarkIcons(final boolean useDarkIcons) {
    updateTransform(transform -> transform.withDark(useDarkIcons));
  }

  public static void setFilter(final ImageFilter filter) {
    updateTransform(transform -> transform.withFilter(filter));
  }

  public static void clearCache() {
    // Copy the transform to trigger update of cached icons
    updateTransform(IconTransform::copy);
  }

  //TODO[kb] support iconsets
  //public static Icon getIcon(@NotNull final String path, @NotNull final String darkVariantPath) {
  //  return new InvariantIcon(getIcon(path), getIcon(darkVariantPath));
  //}

  @NotNull
  public static Icon getIcon(@NonNls @NotNull String path) {
    Class<?> callerClass = ReflectionUtil.getGrandCallerClass();
    assert callerClass != null : path;
    return getIcon(path, callerClass);
  }

  @Nullable
  public static Icon getReflectiveIcon(@NotNull String path, ClassLoader classLoader) {
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
  @Nullable
  public static Icon findIcon(@NonNls @NotNull String path) {
    Class<?> callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) return null;
    return findIcon(path, callerClass);
  }

  @Nullable
  public static Icon findIcon(@NonNls @NotNull String path, boolean strict) {
    Class<?> callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) return null;
    return findIcon(path, callerClass, false, strict);
  }

  @NotNull
  public static Icon getIcon(@NotNull String path, @NotNull final Class<?> aClass) {
    Icon icon = findIcon(path, aClass, aClass.getClassLoader(), HandleNotFound.strict(STRICT_LOCAL.get()), true);
    if (icon == null) {
      LOG.error("Icon cannot be found in '" + path + "', aClass='" + aClass + "'");
    }
    return icon; // [tav] todo: can't fix it
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
    return findIcon(key + ".png", aClass, true, strict);
  }

  /**
   * Might return null if icon was not found.
   * Use only if you expected null return value, otherwise see {@link IconLoader#getIcon(String, Class)}
   */
  @Nullable
  public static Icon findIcon(@NotNull String path, @NotNull Class<?> aClass) {
    return findIcon(path, aClass, aClass.getClassLoader(), HandleNotFound.strict(STRICT_LOCAL.get()), false);
  }

  @Nullable
  public static Icon findIcon(@NotNull String path, @NotNull Class<?> aClass, boolean computeNow) {
    return findIcon(path, aClass, computeNow, STRICT_LOCAL.get());
  }

  @Nullable
  public static Icon findIcon(@NotNull String path, @NotNull Class<?> aClass, boolean computeNow, boolean strict) {
    return findIcon(path, aClass, aClass.getClassLoader(), HandleNotFound.strict(strict), false);
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

  @Nullable
  public static Icon findIcon(URL url) {
    return findIcon(url, true);
  }

  @Nullable
  public static Icon findIcon(URL url, boolean useCache) {
    if (url == null) {
      return null;
    }
    Pair<String, Object> key = Pair.create(ICON_CACHE_URL_KEY, url);
    CachedImageIcon icon = ourIconsCache.get(key);
    if (icon == null) {
      icon = new CachedImageIcon(url, useCache);
      if (useCache) {
        icon = ConcurrencyUtil.cacheOrGet(ourIconsCache, key, icon);
      }
    }
    return icon;
  }

  @Nullable
  private static Icon findIcon(@NotNull String originalPath,
                               @Nullable Class<?> clazz,
                               @NotNull ClassLoader classLoader,
                               @NotNull HandleNotFound handleNotFound,
                               boolean deferUrlResolve) {
    long start = StartUpMeasurer.isEnabled() ? StartUpMeasurer.getCurrentTime() : -1;
    Icon icon = findIconImpl(originalPath, clazz, classLoader, handleNotFound, deferUrlResolve);
    if (start != -1) {
      IconLoadMeasurer.addFindIcon(StartUpMeasurer.getCurrentTime() - start);
    }

    return icon;
  }

  @Nullable
  private static Icon findIconImpl(@NotNull String originalPath,
                                   @Nullable Class<?> clazz,
                                   @NotNull ClassLoader classLoader,
                                   @NotNull HandleNotFound handleNotFound,
                                   boolean deferUrlResolve) {
    Pair<String, ClassLoader> patchedPath = ourTransform.get().patchPath(originalPath, classLoader);
    String path = patchedPath.first;
    if (patchedPath.second != null) {
      classLoader = patchedPath.second;
    }

    if (isReflectivePath(path)) {
      return getReflectiveIcon(path, classLoader);
    }

    Pair<String, Object> key = Pair.create(originalPath, classLoader);
    CachedImageIcon cachedIcon = ourIconsCache.get(key);
    if (cachedIcon == null) {
      cachedIcon = CachedImageIcon.create(originalPath, path, classLoader, clazz, handleNotFound, deferUrlResolve);
      if (cachedIcon == null) {
        return null;
      }
      cachedIcon = ConcurrencyUtil.cacheOrGet(ourIconsCache, key, cachedIcon);
    }
    else {
      ScaleContext scaleContext = ScaleContext.create();
      if (!cachedIcon.getScaleContext().equals(scaleContext)) {
        // honor scale context as 'ourIconsCache' doesn't do that
        cachedIcon = cachedIcon.copy();
        cachedIcon.updateScaleContext(scaleContext);
      }
    }
    return cachedIcon;
  }

  @Nullable
  public static Icon findIcon(@NotNull String path, @NotNull ClassLoader classLoader) {
    return findIcon(path, null, classLoader, HandleNotFound.strict(false), false);
  }

  @Nullable
  public static Image toImage(@NotNull Icon icon) {
    return toImage(icon, null);
  }

  @Nullable
  public static Image toImage(@NotNull Icon icon, @Nullable ScaleContext ctx) {
    if (icon instanceof RetrievableIcon) {
      icon = ((RetrievableIcon)icon).retrieveIcon();
    }
    if (icon instanceof CachedImageIcon) {
      icon = ((CachedImageIcon)icon).getRealIcon(ctx);
    }

    if (icon instanceof ImageIcon) {
      return ((ImageIcon)icon).getImage();
    }
    else {
      BufferedImage image;
      if (GraphicsEnvironment.isHeadless()) { // for testing purpose
        image = UIUtil.createImage(ctx, icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB, ROUND);
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
    if (icon == null) return null;
    if (icon instanceof CopyableIcon) {
      return deepCopy ? ((CopyableIcon)icon).deepCopy() : ((CopyableIcon)icon).copy();
    }
    BufferedImage image = UIUtil.createImage(ancestor, icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      icon.paintIcon(ancestor, g, 0, 0);
    } finally {
      g.dispose();
    }
    return new JBImageIcon(image);
  }

  @Nullable
  private static ImageIcon checkIcon(final @Nullable Image image, @NotNull CachedImageIcon cii) {
    if (image == null || image.getHeight(null) < 1) { // image wasn't loaded or broken
      return null;
    }
    final ImageIcon icon = new JBImageIcon(image);
    if (!isGoodSize(icon)) {
      LOG.error("Invalid icon: " + cii); // # 22481
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
    if (!ourIsActivated) {
      return icon;
    }

    if (icon instanceof LazyIcon) icon = ((LazyIcon)icon).getOrComputeIcon();

    Icon disabledIcon = ourIcon2DisabledIcon.get(icon);
    if (disabledIcon == null) {
      disabledIcon = ConcurrencyUtil.cacheOrGet(ourIcon2DisabledIcon, icon,
           filterIcon(icon, UIUtil::getGrayFilter/* returns laf-aware instance */, null)); // [tav] todo: lack ancestor
    }
    return disabledIcon;
  }

  /**
   * Creates new icon with the filter applied.
   */
  @NotNull
  public static Icon filterIcon(@NotNull Icon icon, @NotNull Supplier<? extends RGBImageFilter> filterSupplier, @Nullable Component ancestor) {
    if (icon instanceof LazyIcon) {
      icon = ((LazyIcon)icon).getOrComputeIcon();
    }

    if (!isGoodSize(icon)) {
      LOG.error(icon); // # 22481
      return EMPTY_ICON;
    }
    if (icon instanceof CachedImageIcon) {
      icon = ((CachedImageIcon)icon).createWithFilter(filterSupplier);
    }
    else {
      double scale;
      ScaleContextSupport ctxSupport = getScaleContextSupport(icon);
      if (ctxSupport != null) {
        scale = ctxSupport.getScale(SYS_SCALE);
      }
      else {
        scale = StartupUiUtil.isJreHiDPI() ? JBUIScale.sysScale(ancestor) : 1f;
      }
      @SuppressWarnings("UndesirableClassUsage")
      BufferedImage image = new BufferedImage((int)(scale * icon.getIconWidth()), (int)(scale * icon.getIconHeight()), BufferedImage.TYPE_INT_ARGB);
      final Graphics2D graphics = image.createGraphics();

      graphics.setColor(UIUtil.TRANSPARENT_COLOR);
      graphics.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
      graphics.scale(scale, scale);
      icon.paintIcon(LabelHolder.ourFakeComponent, graphics, 0, 0);

      graphics.dispose();

      Image img = ImageUtil.filter(image, filterSupplier.get());
      if (StartupUiUtil.isJreHiDPI(ancestor)) img = RetinaImage.createFrom(img, scale, null);

      icon = new JBImageIcon(img);
    }
    return icon;
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
      icon = ((RetrievableIcon)icon).retrieveIcon();
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
    for (Map.Entry<Pair<String, Object>, CachedImageIcon> entry : new ArrayList<>(ourIconsCache.entrySet())) {
      entry.getValue().detachClassLoader(classLoader);
      if (entry.getKey().second == classLoader) {
        CachedImageIcon icon = ourIconsCache.remove(entry.getKey());
        if (icon != null) {
          Icon disabledIcon = ourIcon2DisabledIcon.remove(icon);
          if (disabledIcon instanceof CachedImageIcon) {
            ((CachedImageIcon)disabledIcon).detachClassLoader(classLoader);
          }
        }
      }
    }
  }

  @SuppressWarnings("UnnecessaryFullyQualifiedName")
  public static final class CachedImageIcon extends com.intellij.ui.icons.LazyImageIcon {
    @Nullable private final String myOriginalPath;
    @NotNull private volatile IconUrlResolver myResolver;
    @Nullable("when not overridden") private final Boolean myDarkOverridden;
    private int myTransformModCount;
    private final boolean myUseCacheOnLoad;

    @Nullable private final Supplier<? extends RGBImageFilter> myLocalFilterSupplier;
    private final MyScaledIconsCache myScaledIconsCache = new MyScaledIconsCache();

    public CachedImageIcon(@NotNull URL url) {
      this(url, true);
    }

    CachedImageIcon(@Nullable URL url, boolean useCacheOnLoad) {
      this(new MyUrlResolver(url, null), null, useCacheOnLoad);
    }

    private CachedImageIcon(@NotNull MyUrlResolver urlResolver, @Nullable String originalPath, boolean useCacheOnLoad) {
      this(originalPath, urlResolver, null, useCacheOnLoad, null);
    }

    private CachedImageIcon(@Nullable String originalPath,
                            @NotNull IconUrlResolver resolver,
                            @Nullable Boolean darkOverridden,
                            boolean useCacheOnLoad,
                            @Nullable Supplier<? extends RGBImageFilter> localFilterSupplier) {
      myOriginalPath = originalPath;
      myResolver = resolver;
      myDarkOverridden = darkOverridden;
      myUseCacheOnLoad = useCacheOnLoad;
      myTransformModCount = ourTransformModCount.get();
      myLocalFilterSupplier = localFilterSupplier;
    }

    @Contract("_, _, _, _, _, true -> !null")
    static CachedImageIcon create(@NotNull String originalPath,
                                  @Nullable String pathToResolve,
                                  @NotNull ClassLoader classLoader,
                                  @Nullable Class<?> clazz,
                                  @NotNull HandleNotFound handleNotFound,
                                  boolean deferUrlResolve) {
      MyUrlResolver resolver = new MyUrlResolver(pathToResolve == null ? originalPath : pathToResolve, clazz, classLoader, handleNotFound);
      CachedImageIcon icon = new CachedImageIcon(resolver, originalPath, true);
      if (!deferUrlResolve && icon.getURL() == null) return null;
      return icon;
    }

    @Nullable
    public String getOriginalPath() {
      return myOriginalPath;
    }

    @Override
    @NotNull
    protected ImageIcon getRealIcon(@Nullable ScaleContext ctx) {
      if (!isValid()) {
        if (isLoaderDisabled()) return EMPTY_ICON;
        synchronized (myLock) {
          if (!isValid()) {
            myTransformModCount = ourTransformModCount.get();
            myResolver.resolve();
            myRealIcon = null;
            myScaledIconsCache.clear();
            if (myOriginalPath != null) {
              myResolver = myResolver.patch(myOriginalPath, ourTransform.get());
            }
          }
        }
      }
      Object realIcon = myRealIcon;
      synchronized (myLock) {
        if (!updateScaleContext(ctx) && realIcon != null) {
          // try returning the current icon as the context is up-to-date
          ImageIcon icon = unwrapIcon(realIcon);
          if (icon != null) return icon;
        }

        ImageIcon icon = myScaledIconsCache.getOrScaleIcon(1f);
        if (icon != null) {
          myRealIcon = icon.getIconWidth() < 50 && icon.getIconHeight() < 50 ? icon : new SoftReference<>(icon);
          return icon;
        }
      }
      return EMPTY_ICON;
    }

    private boolean isValid() {
      return ourTransformModCount.get() == myTransformModCount && myResolver.isResolved();
    }

    @Override
    public String toString() {
      if (myResolver.isResolved()) {
        URL url = myResolver.getURL();
        if (url != null) return url.toString();
      }
      return myOriginalPath != null ? myOriginalPath : "unknown path";
    }

    @NotNull
    @Override
    public Icon scale(float scale) {
      if (scale == 1f) return this;

      getRealIcon(); // force state update & cache reset

      Icon icon = myScaledIconsCache.getOrScaleIcon(scale);
      if (icon != null) {
        return icon;
      }
      return this;
    }

    @NotNull
    @Override
    public Icon getDarkIcon(boolean isDark) {
      return new CachedImageIcon(myOriginalPath, myResolver, isDark, myUseCacheOnLoad, myLocalFilterSupplier);
    }

    @NotNull
    @Override
    public Icon getMenuBarIcon(boolean isDark) {
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

    @NotNull
    @Override
    public CachedImageIcon copy() {
      return new CachedImageIcon(myOriginalPath, myResolver, myDarkOverridden, myUseCacheOnLoad, myLocalFilterSupplier);
    }

    @NotNull
    private Icon createWithFilter(@NotNull Supplier<? extends RGBImageFilter> filterSupplier) {
      return new CachedImageIcon(myOriginalPath, myResolver, myDarkOverridden, myUseCacheOnLoad, filterSupplier);
    }

    private boolean isDark() {
      return myDarkOverridden == null ? ourTransform.get().isDark() : myDarkOverridden;
    }

    private ImageFilter @Nullable [] getFilters() {
      ImageFilter global = ourTransform.get().getFilter();
      ImageFilter local = myLocalFilterSupplier != null ? myLocalFilterSupplier.get() : null;
      if (global != null && local != null) {
        return new ImageFilter[] {global, local};
      }
      if (global != null) {
        return new ImageFilter[] {global};
      }
      if (local != null) {
        return new ImageFilter[] {local};
      }
      return null;
    }

    @Nullable
    public URL getURL() {
      return myResolver.getURL();
    }

    @Nullable
    private Image loadFromUrl(@NotNull ScaleContext ctx, boolean dark) {
      long start = StartUpMeasurer.isEnabled() ? StartUpMeasurer.getCurrentTime() : -1;
      Image image = loadFromUrlImpl(ctx, dark);
      if (start != -1) {
        IconLoadMeasurer.addFindIconLoad(StartUpMeasurer.getCurrentTime() - start);
      }
      return image;
    }

    private Image loadFromUrlImpl(@NotNull ScaleContext ctx, boolean dark) {
      int flags = ImageLoader.FIND_SVG | ImageLoader.ALLOW_FLOAT_SCALING;
      if (myUseCacheOnLoad) {
        flags |= ImageLoader.USE_CACHE;
      }
      if (dark) {
        flags |= ImageLoader.DARK;
      }

      String path = myResolver.getOverriddenPath();
      Class<?> aClass = myResolver.getOwnerClass();
      if (aClass != null && path != null) {
        return ImageLoader.loadFromUrl(path, aClass, flags, getFilters(), ctx);
      }

      URL url = getURL();
      if (url == null) {
        return null;
      }
      return ImageLoader.loadFromUrl(url, null, flags, getFilters(), ctx);
    }

    void detachClassLoader(ClassLoader loader) {
      synchronized (myLock) {
        myResolver = myResolver.detachClassLoader(loader);
      }
    }

    private final class MyScaledIconsCache {
      private static final int SCALED_ICONS_CACHE_LIMIT = 5;

      private final Map<Couple<Double>, SoftReference<ImageIcon>> scaledIconsCache = Collections.synchronizedMap(
        new FixedHashMap<>(SCALED_ICONS_CACHE_LIMIT));

      private Couple<Double> key(@NotNull ScaleContext ctx) {
        return new Couple<>(ctx.getScale(DerivedScaleType.EFF_USR_SCALE), ctx.getScale(SYS_SCALE));
      }

      /**
       * Retrieves the orig icon scaled by the provided scale.
       */
      ImageIcon getOrScaleIcon(final float scale) {
        ScaleContext ctx = getScaleContext();
        if (scale != 1) {
          ctx = ctx.copy();
          ctx.setScale(OBJ_SCALE.of(scale));
        }

        ImageIcon icon = SoftReference.dereference(scaledIconsCache.get(key(ctx)));
        if (icon != null) {
          return icon;
        }
        Image image = loadFromUrl(ctx, isDark());
        icon = checkIcon(image, CachedImageIcon.this);

        if (icon != null && 4L * icon.getIconWidth() * icon.getIconHeight() < ImageLoader.CACHED_IMAGE_MAX_SIZE) {
          scaledIconsCache.put(key(ctx), new SoftReference<>(icon));
        }
        return icon;
      }

      public void clear() {
        scaledIconsCache.clear();
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

      @NotNull
      static HandleNotFound strict(boolean strict) {
        return strict ? THROW_EXCEPTION : IGNORE;
      }
    }

    private interface IconUrlResolver {

      @Nullable
      Class<?> getOwnerClass();

      @Nullable
      String getOverriddenPath();

      boolean isResolved();

      IconUrlResolver resolve() throws RuntimeException;

      @Nullable
      URL getURL();

      IconUrlResolver patch(@NotNull String originalPath, @NotNull IconTransform transform);

      IconUrlResolver detachClassLoader(ClassLoader loader);
    }

    /**
     * Used to defer URL resolve.
     */
    private static final class MyUrlResolver implements IconUrlResolver {
      @Nullable private final Class<?> myOwnerClass;
      @Nullable private final ClassLoader myClassLoader;
      @Nullable private final String myOverriddenPath;
      @NotNull private final HandleNotFound myHandleNotFound;
      // Every myUrl write is performed before isResolved write (see resolve())
      // and every myUrl read is performed after isResolved read (see getUrl()), thus
      // no necessary to declare myUrl as volatile: happens-before is established via isResolved.
      @Nullable private URL myUrl;
      private volatile boolean isResolved;

      MyUrlResolver(@Nullable URL url, @Nullable ClassLoader classLoader) {
        myOwnerClass = null;
        myOverriddenPath = null;
        myClassLoader = classLoader;
        myUrl = url;
        myHandleNotFound = HandleNotFound.IGNORE;
        isResolved = true;
      }

      MyUrlResolver(@Nullable URL url, @NotNull String path, @Nullable ClassLoader classLoader) {
        myOwnerClass = null;
        myOverriddenPath = path;
        myClassLoader = classLoader;
        myUrl = url;
        myHandleNotFound = HandleNotFound.IGNORE;
        isResolved = true;
      }

      MyUrlResolver(@NotNull String path, @Nullable Class<?> clazz, @Nullable ClassLoader classLoader, @NotNull HandleNotFound handleNotFound) {
        myOverriddenPath = path;
        myOwnerClass = clazz;
        myClassLoader = classLoader;
        myHandleNotFound = handleNotFound;
        if (!SystemProperties.getBooleanProperty("ide.icons.deferUrlResolve", true)) {
          resolve();
        }
      }

      @Override
      public @Nullable Class<?> getOwnerClass() {
        return myOwnerClass;
      }

      @Override
      @Nullable
      public String getOverriddenPath() {
        return myOverriddenPath;
      }

      @Override
      public boolean isResolved() {
        return isResolved;
      }

      /**
       * Resolves the URL if it's not yet resolved.
       */
      @Override
      public MyUrlResolver resolve() throws RuntimeException {
        if (isResolved) return this;
        try {
          URL url = null;
          String path = myOverriddenPath;
          if (path != null) {
            if (myClassLoader != null) {
              path = StringUtil.trimStart(path, "/"); // Paths in ClassLoader getResource shouldn't start with "/"
              url = findURL(path, myClassLoader::getResource);
            }
            if (url == null && myOwnerClass != null) {
              // Some plugins use findIcon("icon.png",IconContainer.class)
              url = findURL(path, myOwnerClass::getResource);
            }
          }
          if (url == null) {
            myHandleNotFound.handle("Can't find icon in '" + path + "' near " + myClassLoader);
          }
          myUrl = url;
        } finally {
          isResolved = true;
        }
        return this;
      }

      @Override
      @Nullable
      public URL getURL() {
        if (!isResolved()) {
          return resolve().myUrl;
        }
        return myUrl;
      }

      @Override
      public IconUrlResolver patch(@NotNull String originalPath, @NotNull IconTransform transform) {
        Pair<String, ClassLoader> patchedPath = transform.patchPath(originalPath, myClassLoader);
        ClassLoader classLoader = patchedPath.second != null ? patchedPath.second : myClassLoader;
        String path = patchedPath.first;
        if (classLoader != null && path != null && path.startsWith("/")) {
          return new MyUrlResolver(path.substring(1), null, classLoader, myHandleNotFound).resolve();
        }

        //This use case for temp themes only. Here we want immediately replace existing icon to a local one
        if (path != null && path.startsWith("file:/")) {
          try {
            return new MyUrlResolver(new URL(path), path.substring(1), classLoader).resolve();
          } catch (MalformedURLException ignore) {}
        }

        return this;
      }

      @Nullable
      @SuppressWarnings("DuplicateExpressions")
      private static URL findURL(@NotNull String path, @NotNull Function<? super String, URL> urlProvider) {
        URL url = urlProvider.apply(path);
        if (url != null) return url;

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
      public IconUrlResolver detachClassLoader(ClassLoader loader) {
        if (myClassLoader == loader) {
          return new DummyUrlResolver();
        }
        return this;
      }
    }

    private static class DummyUrlResolver implements IconUrlResolver {
      @Override
      public @Nullable Class<?> getOwnerClass() {
        return null;
      }

      @Nullable
      @Override
      public String getOverriddenPath() {
        return null;
      }

      @Override
      public boolean isResolved() {
        return false;
      }

      @Override
      public IconUrlResolver resolve() throws RuntimeException {
        return this;
      }

      @Nullable
      @Override
      public URL getURL() {
        return null;
      }

      @Override
      public IconUrlResolver patch(@NotNull String originalPath, @NotNull IconTransform transform) {
        return this;
      }

      @Override
      public IconUrlResolver detachClassLoader(ClassLoader loader) {
        return this;
      }
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

  /**
   * Consider using {@link #createLazy)}.
   */
  public abstract static class LazyIcon extends ScaleContextSupport implements CopyableIcon, RetrievableIcon {
    private boolean myWasComputed;
    private volatile Icon myIcon;
    private int myTransformModCount = ourTransformModCount.get();

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
      int newTransformModCount = ourTransformModCount.get();
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
      return getScaleContextSupport(((CompositeIcon)icon).getIcon(0));
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

    @NotNull
    public Pair<String, ClassLoader> patchPath(@NotNull String path, ClassLoader classLoader) {
      for (IconPathPatcher patcher : myPatchers) {
        String newPath = patcher.patchPath(path, classLoader);
        if (newPath == null) {
          newPath = patcher.patchPath(path, null);
        }
        if (newPath != null) {
          LOG.debug("replace '" + path + "' with '" + newPath + "'");
          ClassLoader contextClassLoader = patcher.getContextClassLoader(path, classLoader);
          if (contextClassLoader == null) {
            //noinspection deprecation
            Class<?> contextClass = patcher.getContextClass(path);
            if (contextClass != null) {
              contextClassLoader = contextClass.getClassLoader();
            }
          }
          return Pair.create(newPath, contextClassLoader);
        }
      }
      return Pair.create(path, null);
    }

    @NotNull
    public IconTransform copy() {
      return new IconTransform(myDark, myPatchers, myFilter);
    }

    @NotNull
    public static IconTransform getDefault() {
      return new IconTransform(StartupUiUtil.isUnderDarcula(), new IconPathPatcher[0], null);
    }
  }
}
