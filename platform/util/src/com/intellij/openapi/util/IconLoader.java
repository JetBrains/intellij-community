// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.RetrievableIcon;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ImageLoader;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.RetinaImage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.BaseScaleContext.UpdateListener;
import com.intellij.util.ui.JBUI.RasterJBIcon;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageFilter;
import java.awt.image.RGBImageFilter;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static com.intellij.util.ui.JBUI.ScaleType.*;

public final class IconLoader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.IconLoader");
  private static final String LAF_PREFIX = "/com/intellij/ide/ui/laf/icons/";
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static final ConcurrentMap<URL, CachedImageIcon> ourIconsCache = ContainerUtil.newConcurrentMap(100, 0.9f, 2);
  /**
   * This cache contains mapping between icons and disabled icons.
   */
  private static final Map<Icon, Icon> ourIcon2DisabledIcon = ContainerUtil.createWeakMap(200);
  @NonNls private static final List<IconPathPatcher> ourPatchers = new ArrayList<IconPathPatcher>(2);
  public static boolean STRICT;

  private static boolean USE_DARK_ICONS = UIUtil.isUnderDarcula();

  private static ImageFilter IMAGE_FILTER;

  private volatile static int clearCacheCounter;

  static {
    installPathPatcher(new DeprecatedDuplicatesIconPathPatcher());
  }

  private static final ImageIcon EMPTY_ICON = new ImageIcon(UIUtil.createImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)) {
    @NonNls
    public String toString() {
      return "Empty icon " + super.toString();
    }
  };

  private static boolean ourIsActivated;

  private IconLoader() { }

  public static void installPathPatcher(@NotNull IconPathPatcher patcher) {
    ourPatchers.add(patcher);
    clearCache();
  }

  @Deprecated
  @NotNull
  public static Icon getIcon(@NotNull final Image image) {
    return new JBImageIcon(image);
  }

  public static void setUseDarkIcons(boolean useDarkIcons) {
    USE_DARK_ICONS = useDarkIcons;
    clearCache();
  }

  public static void setFilter(ImageFilter filter) {
    if (IMAGE_FILTER != filter) {
      IMAGE_FILTER = filter;
      clearCache();
    }
  }

  public static void clearCache() {
    ourIconsCache.clear();
    ourIcon2DisabledIcon.clear();
    clearCacheCounter++;
  }

  //TODO[kb] support iconsets
  //public static Icon getIcon(@NotNull final String path, @NotNull final String darkVariantPath) {
  //  return new InvariantIcon(getIcon(path), getIcon(darkVariantPath));
  //}

  @NotNull
  public static Icon getIcon(@NonNls @NotNull final String path) {
    Class callerClass = ReflectionUtil.getGrandCallerClass();

    assert callerClass != null : path;
    return getIcon(path, callerClass);
  }

  @Nullable
  private static Icon getReflectiveIcon(@NotNull String path, ClassLoader classLoader) {
    try {
      @NonNls String pckg = path.startsWith("AllIcons.") ? "com.intellij.icons." : "icons.";
      Class cur = Class.forName(pckg + path.substring(0, path.lastIndexOf('.')).replace('.', '$'), true, classLoader);
      Field field = cur.getField(path.substring(path.lastIndexOf('.') + 1));

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
    Class callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) return null;
    return findIcon(path, callerClass);
  }

  @Nullable
  public static Icon findIcon(@NonNls @NotNull String path, boolean strict) {
    Class callerClass = ReflectionUtil.getGrandCallerClass();
    if (callerClass == null) return null;
    return findIcon(path, callerClass, false, strict);
  }

  @NotNull
  public static Icon getIcon(@NotNull String path, @NotNull final Class aClass) {
    final Icon icon = findIcon(path, aClass);
    if (icon == null) {
      LOG.error("Icon cannot be found in '" + path + "', aClass='" + aClass + "'");
    }
    return icon;
  }

  public static void activate() {
    ourIsActivated = true;
  }

  private static boolean isLoaderDisabled() {
    return !ourIsActivated;
  }

  @Nullable
  public static Icon findLafIcon(@NotNull String key, @NotNull Class aClass) {
    return findLafIcon(key, aClass, STRICT);
  }

  @Nullable
  public static Icon findLafIcon(@NotNull String key, @NotNull Class aClass, boolean strict) {
    return findIcon(LAF_PREFIX + key + ".png", aClass, true, strict);
  }

  /**
   * Might return null if icon was not found.
   * Use only if you expected null return value, otherwise see {@link IconLoader#getIcon(String, Class)}
   */
  @Nullable
  public static Icon findIcon(@NotNull final String path, @NotNull final Class aClass) {
    return findIcon(path, aClass, false);
  }

  @Nullable
  public static Icon findIcon(@NotNull String path, @NotNull final Class aClass, boolean computeNow) {
    return findIcon(path, aClass, computeNow, STRICT);
  }

  @Nullable
  public static Icon findIcon(@NotNull String path, @NotNull Class aClass, boolean computeNow, boolean strict) {
    String originalPath = path;
    ClassLoader classLoader = aClass.getClassLoader();
    Pair<String, ClassLoader> patchedPath = patchPath(path, classLoader);
    path = patchedPath.first;
    if (patchedPath.second != null) {
      classLoader = patchedPath.second;
    }
    if (isReflectivePath(path)) return getReflectiveIcon(path, classLoader);

    URL myURL = findURL(path, classLoader);
    if (myURL == null) {
      if (strict) throw new RuntimeException("Can't find icon in '" + path + "' near " + aClass);
      return null;
    }
    final Icon icon = findIcon(myURL);
    if (icon instanceof CachedImageIcon) {
      ((CachedImageIcon)icon).myOriginalPath = originalPath;
      ((CachedImageIcon)icon).myClassLoader = classLoader;
    }
    return icon;
  }

  @NotNull
  private static Pair<String, ClassLoader> patchPath(@NotNull String path, ClassLoader classLoader) {
    for (IconPathPatcher patcher : ourPatchers) {
      String newPath = patcher.patchPath(path, classLoader);
      if (newPath == null) {
        newPath = patcher.patchPath(path);
      }
      if (newPath != null) {
        LOG.info("replace '" + path + "' with '" + newPath + "'");
        return Pair.create(newPath, patcher.getContextClassLoader(path, classLoader));
      }
    }
    return Pair.create(path, null);
  }

  private static boolean isReflectivePath(@NotNull String path) {
    List<String> paths = StringUtil.split(path, ".");
    return paths.size() > 1 && paths.get(0).endsWith("Icons");
  }

  @Nullable
  private static URL findURL(@NotNull String path, @Nullable Object context) {
    URL url;
    if (context instanceof Class) {
      url = ((Class)context).getResource(path);
    }
    else if (context instanceof ClassLoader) {
      // Paths in ClassLoader getResource shouldn't start with "/"
      url = ((ClassLoader)context).getResource(path.startsWith("/") ? path.substring(1) : path);
    }
    else {
      LOG.warn("unexpected: " + context);
      return null;
    }
    // Find either PNG or SVG icon. The icon will then be wrapped into CachedImageIcon
    // which will load proper icon version depending on the context - UI theme, DPI.
    // SVG version, when present, has more priority than PNG.
    // See for details: com.intellij.util.ImageLoader.ImageDescList#create
    if (url != null || !path.endsWith(".png")) return url;
    url = findURL(path.substring(0, path.length() - 4) + ".svg", context);
    if (url != null && !path.startsWith(LAF_PREFIX)) LOG.info("replace '" + path + "' with '" + url + "'");
    return url;
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
    CachedImageIcon icon = ourIconsCache.get(url);
    if (icon == null) {
      icon = new CachedImageIcon(url, useCache);
      if (useCache) {
        icon = ConcurrencyUtil.cacheOrGet(ourIconsCache, url, icon);
      }
    }
    return icon;
  }

  @Nullable
  public static Icon findIcon(@NotNull String path, @NotNull ClassLoader classLoader) {
    String originalPath = path;
    Pair<String, ClassLoader> patchedPath = patchPath(path, null);
    path = patchedPath.first;
    if (patchedPath.second != null) {
      classLoader = patchedPath.second;
    }
    if (isReflectivePath(path)) return getReflectiveIcon(path, classLoader);
    if (!StringUtil.startsWithChar(path, '/')) return null;

    final URL url = findURL(path.substring(1), classLoader);
    final Icon icon = findIcon(url);
    if (icon instanceof CachedImageIcon) {
      ((CachedImageIcon)icon).myOriginalPath = originalPath;
      ((CachedImageIcon)icon).myClassLoader = classLoader;
    }
    return icon;
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
        image = UIUtil.createImage(ctx, icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB, RoundingMode.FLOOR);
      } else {
        // [tav] todo: match the screen with the provided ctx
        image = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                   .getDefaultScreenDevice().getDefaultConfiguration()
                                   .createCompatibleImage(icon.getIconWidth(), icon.getIconHeight(), Transparency.TRANSLUCENT);
      }
      Graphics2D g = image.createGraphics();
      try {
        icon.paintIcon(null, g, 0, 0);
      } finally {
        g.dispose();
      }
      return image;
    }
  }

  @Contract("null, _->null; !null, _->!null")
  public static Icon copy(@Nullable Icon icon, @Nullable Component ancestor) {
    if (icon == null) return null;
    if (icon instanceof CopyableIcon) {
      return ((CopyableIcon)icon).copy();
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
  private static ImageIcon checkIcon(final Image image, @NotNull URL url) {
    if (image == null || image.getHeight(null) < 1) { // image wasn't loaded or broken
      return null;
    }

    final Icon icon = getIcon(image);
    if (!isGoodSize(icon)) {
      LOG.error("Invalid icon: " + url); // # 22481
      return EMPTY_ICON;
    }
    assert icon instanceof ImageIcon;
    return (ImageIcon)icon;
  }

  public static boolean isGoodSize(@NotNull final Icon icon) {
    return icon.getIconWidth() > 0 && icon.getIconHeight() > 0;
  }

  /**
   * Gets (creates if necessary) disabled icon based on the passed one.
   *
   * @return {@code ImageIcon} constructed from disabled image of passed icon.
   */
  @Nullable
  public static Icon getDisabledIcon(Icon icon) {
    if (icon instanceof LazyIcon) icon = ((LazyIcon)icon).getOrComputeIcon();
    if (icon == null) return null;

    Icon disabledIcon = ourIcon2DisabledIcon.get(icon);
    if (disabledIcon == null) {
      disabledIcon = filterIcon(icon, UIUtil.getGrayFilter(), null); // [tav] todo: lack ancestor
      ourIcon2DisabledIcon.put(icon, disabledIcon);
    }
    return disabledIcon;
  }

  /**
   * Creates new icon with the filter applied.
   */
  @Nullable
  public static Icon filterIcon(@NotNull Icon icon, RGBImageFilter filter, @Nullable Component ancestor) {
    if (icon instanceof LazyIcon) icon = ((LazyIcon)icon).getOrComputeIcon();
    if (icon == null) return null;

    if (!isGoodSize(icon)) {
      LOG.error(icon); // # 22481
      return EMPTY_ICON;
    }
    if (icon instanceof CachedImageIcon) {
      icon = ((CachedImageIcon)icon).createWithFilter(filter);
    } else {
      final float scale;
      if (icon instanceof JBUI.ScaleContextAware) {
        scale = (float)((JBUI.ScaleContextAware)icon).getScale(SYS_SCALE);
      }
      else {
        scale = UIUtil.isJreHiDPI() ? JBUI.sysScale(ancestor) : 1f;
      }
      @SuppressWarnings("UndesirableClassUsage")
      BufferedImage image = new BufferedImage((int)(scale * icon.getIconWidth()), (int)(scale * icon.getIconHeight()), BufferedImage.TYPE_INT_ARGB);
      final Graphics2D graphics = image.createGraphics();

      graphics.setColor(UIUtil.TRANSPARENT_COLOR);
      graphics.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
      graphics.scale(scale, scale);
      icon.paintIcon(LabelHolder.ourFakeComponent, graphics, 0, 0);

      graphics.dispose();

      Image img = ImageUtil.filter(image, filter);
      if (UIUtil.isJreHiDPI(ancestor)) img = RetinaImage.createFrom(img, scale, null);

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
  public static Icon getMenuBarIcon(Icon icon, boolean dark) {
    if (icon instanceof RetrievableIcon) {
      icon = ((RetrievableIcon)icon).retrieveIcon();
    }
    if (icon instanceof MenuBarIconProvider) {
      return ((MenuBarIconProvider)icon).getMenuBarIcon(dark);
    }
    if (icon instanceof CachedImageIcon) {
      Image img = ((CachedImageIcon)icon).loadFromUrl(ScaleContext.createIdentity(), dark);
      if (img != null) {
        icon = new ImageIcon(img);
      }
    }
    return icon;
  }

  /**
   * Returns a copy of the provided {@code icon} with darkness set to {@code dark}.
   * The method takes effect on a {@link CachedImageIcon} (or its wrapper) only.
   */
  public static Icon getDarkIcon(Icon icon, boolean dark) {
    if (icon instanceof RetrievableIcon) {
      icon = ((RetrievableIcon)icon).retrieveIcon();
    }
    if (icon instanceof CachedImageIcon) {
      icon = ((CachedImageIcon)icon).copy();
      ((CachedImageIcon)icon).setDark(dark);
    }
    return icon;
  }

  public static final class CachedImageIcon extends RasterJBIcon implements ScalableIcon {
    private volatile Object myRealIcon;
    private String myOriginalPath;
    private ClassLoader myClassLoader;
    @NotNull
    private URL myUrl;
    private volatile boolean myDark;
    private volatile boolean myDarkOverriden;
    private volatile int numberOfPatchers = ourPatchers.size();
    private final boolean useCacheOnLoad;
    private int myClearCacheCounter = clearCacheCounter;

    private ImageFilter[] myFilters;
    private final MyScaledIconsCache myScaledIconsCache = new MyScaledIconsCache();

    {
      // For instance, ShadowPainter updates the context from outside.
      getScaleContext().addUpdateListener(new UpdateListener() {
        @Override
        public void contextUpdated() {
          myRealIcon = null;
        }
      });
    }

    private CachedImageIcon(@NotNull CachedImageIcon icon) {
      myRealIcon = null; // to be computed
      myOriginalPath = icon.myOriginalPath;
      myClassLoader = icon.myClassLoader;
      myUrl = icon.myUrl;
      myDark = icon.myDark;
      myDarkOverriden = icon.myDarkOverriden;
      numberOfPatchers = icon.numberOfPatchers;
      myFilters = icon.myFilters;
      useCacheOnLoad = icon.useCacheOnLoad;
      myClearCacheCounter = icon.myClearCacheCounter;
    }

    public CachedImageIcon(@NotNull URL url) {
      this(url, true);
    }

    public CachedImageIcon(@NotNull URL url, boolean useCacheOnLoad) {
      myUrl = url;
      myDark = USE_DARK_ICONS;
      myFilters = new ImageFilter[] {IMAGE_FILTER};
      this.useCacheOnLoad = useCacheOnLoad;
    }

    private void setGlobalFilter(ImageFilter globalFilter) {
      myFilters[0] = globalFilter;
    }

    private ImageFilter getGlobalFilter() {
      return myFilters[0];
    }

    @NotNull
    private synchronized ImageIcon getRealIcon() {
      return getRealIcon(null);
    }

    @Nullable
    @TestOnly
    public ImageIcon doGetRealIcon() {
      Object icon = myRealIcon;
      if (icon instanceof Reference) {
        icon = ((Reference<ImageIcon>)icon).get();
      }
      return icon instanceof ImageIcon ? (ImageIcon)icon : null;
    }

    @NotNull
    private synchronized ImageIcon getRealIcon(@Nullable ScaleContext ctx) {
      if (!isValid()) {
        if (isLoaderDisabled()) return EMPTY_ICON;
        myClearCacheCounter = clearCacheCounter;
        myRealIcon = null;
        if (!myDarkOverriden) myDark = USE_DARK_ICONS;
        setGlobalFilter(IMAGE_FILTER);
        myScaledIconsCache.clear();
        if (numberOfPatchers != ourPatchers.size()) {
          numberOfPatchers = ourPatchers.size();
          Pair<String, ClassLoader> patchedPath = patchPath(myOriginalPath, null);
          String path = myOriginalPath == null ? null : patchedPath.first;
          if (patchedPath.second != null) {
            myClassLoader = patchedPath.second;
          }
          if (myClassLoader != null && path != null && path.startsWith("/")) {
            path = path.substring(1);
            URL url = findURL(path, myClassLoader);
            if (url != null) {
              myUrl = url;
            }
          }
        }
      }
      if (!updateScaleContext(ctx) && myRealIcon != null) {
        // try returning the current icon as the context is up-to-date
        Object icon = myRealIcon;
        if (icon instanceof Reference) icon = ((Reference<ImageIcon>)icon).get();
        if (icon instanceof ImageIcon) return (ImageIcon)icon;
      }

      ImageIcon icon = myScaledIconsCache.getOrScaleIcon(1f);
      if (icon != null) {
        myRealIcon = icon.getIconWidth() < 50 && icon.getIconHeight() < 50 ? icon : new SoftReference<ImageIcon>(icon);
        return icon;
      }
      return EMPTY_ICON;
    }

    private boolean isValid() {
      return (!myDarkOverriden && myDark == USE_DARK_ICONS) &&
             getGlobalFilter() == IMAGE_FILTER &&
             numberOfPatchers == ourPatchers.size() &&
             myClearCacheCounter == clearCacheCounter;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2d = g instanceof Graphics2D ? (Graphics2D)g : null;
      getRealIcon(ScaleContext.create(c, g2d)).paintIcon(c, g, x, y);
    }

    @Override
    public int getIconWidth() {
      return getRealIcon().getIconWidth();
    }

    @Override
    public int getIconHeight() {
      return getRealIcon().getIconHeight();
    }

    @Override
    public String toString() {
      return myUrl.toString();
    }

    @Override
    public float getScale() {
      return 1f;
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

    private synchronized void setDark(boolean dark) {
      myDarkOverriden = true;
      if (myDark != dark) {
        myRealIcon = null;
        myClearCacheCounter = -1;
        myDark = dark;
      }
    }

    @NotNull
    @Override
    public CachedImageIcon copy() {
      return new CachedImageIcon(this);
    }

    @NotNull
    private Icon createWithFilter(@NotNull RGBImageFilter filter) {
      CachedImageIcon icon = new CachedImageIcon(this);
      icon.myFilters = new ImageFilter[] {getGlobalFilter(), filter};
      return icon;
    }

    private Image loadFromUrl(@NotNull ScaleContext ctx, boolean dark) {
      return ImageLoader.loadFromUrl(myUrl, true, useCacheOnLoad, dark, myFilters, ctx);
    }

    private class MyScaledIconsCache {
      private static final int SCALED_ICONS_CACHE_LIMIT = 5;

      private final Map<Couple<Double>, SoftReference<ImageIcon>> scaledIconsCache = Collections.synchronizedMap(new LinkedHashMap<Couple<Double>, SoftReference<ImageIcon>>(SCALED_ICONS_CACHE_LIMIT) {
        @Override
        public boolean removeEldestEntry(Map.Entry<Couple<Double>, SoftReference<ImageIcon>> entry) {
          return size() > SCALED_ICONS_CACHE_LIMIT;
        }
      });

      private Couple<Double> key(@NotNull ScaleContext ctx) {
        return new Couple<Double>(ctx.getScale(USR_SCALE) * ctx.getScale(OBJ_SCALE), ctx.getScale(SYS_SCALE));
      }

      /**
       * Retrieves the orig icon scaled by the provided scale.
       */
      ImageIcon getOrScaleIcon(final float scale) {
        ScaleContext ctx = getScaleContext();
        if (scale != 1) {
          ctx = ctx.copy();
          ctx.update(OBJ_SCALE.of(scale));
        }

        ImageIcon icon = SoftReference.dereference(scaledIconsCache.get(key(ctx)));
        if (icon != null) {
          return icon;
        }
        Image image = loadFromUrl(ctx, myDark);
        icon = checkIcon(image, myUrl);

        if (icon != null && icon.getIconWidth() * icon.getIconHeight() * 4 < ImageLoader.CACHED_IMAGE_MAX_SIZE) {
          scaledIconsCache.put(key(ctx), new SoftReference<ImageIcon>(icon));
        }
        return icon;
      }

      public void clear() {
        scaledIconsCache.clear();
      }
    }
  }

  public abstract static class LazyIcon extends RasterJBIcon implements RetrievableIcon {
    private boolean myWasComputed;
    private Icon myIcon;
    private boolean isDarkVariant = USE_DARK_ICONS;
    private int numberOfPatchers = ourPatchers.size();
    private ImageFilter filter = IMAGE_FILTER;

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      if (updateScaleContext(ScaleContext.create((Graphics2D)g))) {
        myIcon = null;
      }
      final Icon icon = getOrComputeIcon();
      if (icon != null) {
        icon.paintIcon(c, g, x, y);
      }
    }

    @Override
    public int getIconWidth() {
      final Icon icon = getOrComputeIcon();
      return icon != null ? icon.getIconWidth() : 0;
    }

    @Override
    public int getIconHeight() {
      final Icon icon = getOrComputeIcon();
      return icon != null ? icon.getIconHeight() : 0;
    }

    protected final synchronized Icon getOrComputeIcon() {
      if (!myWasComputed || isDarkVariant != USE_DARK_ICONS ||
          myIcon == null ||
          filter != IMAGE_FILTER || numberOfPatchers != ourPatchers.size())
      {
        isDarkVariant = USE_DARK_ICONS;
        filter = IMAGE_FILTER;
        myWasComputed = true;
        numberOfPatchers = ourPatchers.size();
        myIcon = compute();
      }

      return myIcon;
    }

    public final void load() {
      getIconWidth();
    }

    protected abstract Icon compute();

    @Nullable
    @Override
    public Icon retrieveIcon() {
      return getOrComputeIcon();
    }

    @NotNull
    @Override
    public Icon copy() {
      return IconLoader.copy(getOrComputeIcon(), null);
    }
  }

  public interface MenuBarIconProvider {
    Icon getMenuBarIcon(boolean isDark);
  }


  private static class LabelHolder {
    /**
     * To get disabled icon with paint it into the image. Some icons require
     * not null component to paint.
     */
    private static final JComponent ourFakeComponent = new JLabel();
  }
}
