/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.RetrievableIcon;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.ScaleType;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageFilter;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;

public final class IconLoader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.IconLoader");
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static final ConcurrentMap<URL, CachedImageIcon> ourIconsCache = ContainerUtil.newConcurrentMap(100, 0.9f, 2);
  /**
   * This cache contains mapping between icons and disabled icons.
   */
  private static final Map<Icon, Icon> ourIcon2DisabledIcon = new WeakHashMap<Icon, Icon>(200);
  @NonNls private static final List<IconPathPatcher> ourPatchers = new ArrayList<IconPathPatcher>(2);
  public static boolean STRICT = false;

  private static boolean USE_DARK_ICONS = UIUtil.isUnderDarcula();

  private static ImageFilter IMAGE_FILTER;

  static {
    installPathPatcher(new DeprecatedDuplicatesIconPathPatcher());
  }

  private static final ImageIcon EMPTY_ICON = new ImageIcon(UIUtil.createImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)) {
    @NonNls
    public String toString() {
      return "Empty icon " + super.toString();
    }
  };

  private static boolean ourIsActivated = false;

  private IconLoader() { }

  public static void installPathPatcher(IconPathPatcher patcher) {
    ourPatchers.add(patcher);
    clearCache();
  }

  @Deprecated
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

  private static void clearCache() {
    ourIconsCache.clear();
    ourIcon2DisabledIcon.clear();
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
    Pair<String, Class> patchedPath = patchPath(path);
    path = patchedPath.first;
    if (patchedPath.second != null) {
      aClass = patchedPath.second;
    }
    if (isReflectivePath(path)) return getReflectiveIcon(path, aClass.getClassLoader());

    URL myURL = aClass.getResource(path);
    if (myURL == null) {
      if (strict) throw new RuntimeException("Can't find icon in '" + path + "' near " + aClass);
      return null;
    }
    final Icon icon = findIcon(myURL);
    if (icon instanceof CachedImageIcon) {
      ((CachedImageIcon)icon).myOriginalPath = originalPath;
      ((CachedImageIcon)icon).myClassLoader = aClass.getClassLoader();
    }
    return icon;
  }

  @NotNull
  private static Pair<String, Class> patchPath(@NotNull String path) {
    for (IconPathPatcher patcher : ourPatchers) {
      String newPath = patcher.patchPath(path);
      if (newPath != null) {
        return Pair.create(newPath, patcher.getContextClass(path));
      }
    }
    return Pair.create(path, null);
  }

  private static boolean isReflectivePath(@NotNull String path) {
    List<String> paths = StringUtil.split(path, ".");
    return paths.size() > 1 && paths.get(0).endsWith("Icons");
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
      icon = new CachedImageIcon(url);
      if (useCache) {
        icon = ConcurrencyUtil.cacheOrGet(ourIconsCache, url, icon);
      }
    }
    return icon;
  }

  @Nullable
  public static Icon findIcon(@NotNull String path, @NotNull ClassLoader classLoader) {
    String originalPath = path;
    Pair<String, Class> patchedPath = patchPath(path);
    path = patchedPath.first;
    if (patchedPath.second != null) {
      classLoader = patchedPath.second.getClassLoader();
    }
    if (isReflectivePath(path)) return getReflectiveIcon(path, classLoader);
    if (!StringUtil.startsWithChar(path, '/')) return null;

    final URL url = classLoader.getResource(path.substring(1));
    final Icon icon = findIcon(url);
    if (icon instanceof CachedImageIcon) {
      ((CachedImageIcon)icon).myOriginalPath = originalPath;
      ((CachedImageIcon)icon).myClassLoader = classLoader;
    }
    return icon;
  }

  @Nullable
  public static Image toImage(@NotNull Icon icon) {
    if (icon instanceof CachedImageIcon) {
      icon = ((CachedImageIcon)icon).getRealIcon();
    }
    if (icon instanceof ImageIcon) {
      return ((ImageIcon)icon).getImage();
    }
    else {
      final int w = icon.getIconWidth();
      final int h = icon.getIconHeight();
      final BufferedImage image = GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(w, h, Transparency.TRANSLUCENT);
      final Graphics2D g = image.createGraphics();
      icon.paintIcon(null, g, 0, 0);
      g.dispose();
      return image;
    }
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
      if (!isGoodSize(icon)) {
        LOG.error(icon); // # 22481
        return EMPTY_ICON;
      }
      if (icon instanceof CachedImageIcon) {
        disabledIcon = ((CachedImageIcon)icon).asDisabledIcon();
      } else {
        final float scale = UIUtil.isJreHiDPI() ? JBUI.sysScale() : 1f;  // [tav] todo: no screen available
        @SuppressWarnings("UndesirableClassUsage")
        BufferedImage image = new BufferedImage((int)(scale * icon.getIconWidth()), (int)(scale * icon.getIconHeight()), BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();

        graphics.setColor(UIUtil.TRANSPARENT_COLOR);
        graphics.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
        graphics.scale(scale, scale);
        icon.paintIcon(LabelHolder.ourFakeComponent, graphics, 0, 0);

        graphics.dispose();

        Image img = ImageUtil.filter(image, UIUtil.getGrayFilter());
        if (UIUtil.isJreHiDPI()) img = RetinaImage.createFrom(img, scale, null);

        disabledIcon = new JBImageIcon(img);
      }
      ourIcon2DisabledIcon.put(icon, disabledIcon);
    }
    return disabledIcon;
  }

  public static Icon getTransparentIcon(@NotNull final Icon icon) {
    return getTransparentIcon(icon, 0.5f);
  }

  public static Icon getTransparentIcon(@NotNull final Icon icon, final float alpha) {
    return new RetrievableIcon() {
      @Nullable
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
   * {@link IconLoader#setFilter(ImageFilter)}, {@link IconLoader#setUseDarkIcons(boolean)}
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

  private static final class CachedImageIcon extends JBUI.UpdatingJBIcon implements ScalableIcon {
    private volatile Object myRealIcon;
    private String myOriginalPath;
    private ClassLoader myClassLoader;
    @NotNull
    private URL myUrl;
    private volatile boolean dark;
    private volatile int numberOfPatchers = ourPatchers.size();
    private boolean svg;

    private ImageFilter[] myFilters;
    private final MyScaledIconsCache myScaledIconsCache = new MyScaledIconsCache();

    private CachedImageIcon(@NotNull CachedImageIcon icon) {
      myRealIcon = null; // to be computed
      myOriginalPath = icon.myOriginalPath;
      myClassLoader = icon.myClassLoader;
      myUrl = icon.myUrl;
      dark = icon.dark;
      numberOfPatchers = icon.numberOfPatchers;
      myFilters = icon.myFilters;
      svg = myOriginalPath != null ? myOriginalPath.toLowerCase().endsWith("svg") : false;
    }

    public CachedImageIcon(@NotNull URL url) {
      myUrl = url;
      dark = USE_DARK_ICONS;
      myFilters = new ImageFilter[] {IMAGE_FILTER};
      svg = url.toString().endsWith("svg");
    }

    private void setGlobalFilter(ImageFilter globalFilter) {
      myFilters[0] = globalFilter;
    }

    private ImageFilter getGlobalFilter() {
      return myFilters[0];
    }

    @Override
    public boolean updateJBUIScale(Graphics2D g) {
      if (needUpdateJBUIScale(g)) {
        getRealIcon(g); // force update
        return true;
      }
      return false;
    }

    @NotNull
    private synchronized ImageIcon getRealIcon() {
      return getRealIcon(null);
    }

    @NotNull
    private synchronized ImageIcon getRealIcon(@Nullable Graphics g) {
      if (!isValid() || needUpdateJBUIScale((Graphics2D)g)) {
        if (isLoaderDisabled()) return EMPTY_ICON;
        myRealIcon = null;
        dark = USE_DARK_ICONS;
        super.updateJBUIScale((Graphics2D)g);
        setGlobalFilter(IMAGE_FILTER);
        if (!isValid()) myScaledIconsCache.clear();
        if (numberOfPatchers != ourPatchers.size()) {
          numberOfPatchers = ourPatchers.size();
          Pair<String, Class> patchedPath = patchPath(myOriginalPath);
          String path = myOriginalPath == null ? null : patchedPath.first;
          if (patchedPath.second != null) {
            myClassLoader = patchedPath.second.getClassLoader();
          }
          if (myClassLoader != null && path != null && path.startsWith("/")) {
            path = path.substring(1);
            final URL url = myClassLoader.getResource(path);
            if (url != null) {
              myUrl = url;
            }
          }
        }
      }
      Object realIcon = myRealIcon;
      if (realIcon instanceof Icon) return (ImageIcon)realIcon;

      ImageIcon icon;
      if (realIcon instanceof Reference) {
        icon = ((Reference<ImageIcon>)realIcon).get();
        if (icon != null) return icon;
      }

      icon = myScaledIconsCache.getOrLoadIcon(getJBUIScale(ScaleType.PIX));

      if (icon != null) {
        if (icon.getIconWidth() < 50 && icon.getIconHeight() < 50) {
          realIcon = icon;
        }
        else {
          realIcon = new SoftReference<ImageIcon>(icon);
        }
        myRealIcon = realIcon;
      }

      return icon == null ? EMPTY_ICON : icon;
    }

    private boolean isValid() {
      return myRealIcon != null && dark == USE_DARK_ICONS && getGlobalFilter() == IMAGE_FILTER && numberOfPatchers == ourPatchers.size();
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      getRealIcon(g).paintIcon(c, g, x, y);
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

    @Override
    public Icon scale(float scale) {
      if (scale == 1f) return this;

      getRealIcon(); // force state update & cache reset

      Icon icon = myScaledIconsCache.getOrScaleIcon(getJBUIScale(ScaleType.PIX), scale);
      if (icon != null) {
        return icon;
      }
      return this;
    }

    private Icon asDisabledIcon() {
      CachedImageIcon icon = new CachedImageIcon(this);
      icon.myFilters = new ImageFilter[] {getGlobalFilter(), UIUtil.getGrayFilter()};
      return icon;
    }

    private class MyScaledIconsCache {
      // Map {false -> image}, {true -> image@2x}
      private Map<Boolean, SoftReference<Image>> origImagesCache = Collections.synchronizedMap(new HashMap<Boolean, SoftReference<Image>>(2));

      private static final int SCALED_ICONS_CACHE_LIMIT = 5;

      // Map {pixel scale -> icon}
      private Map<Float, SoftReference<ImageIcon>> scaledIconsCache = Collections.synchronizedMap(new LinkedHashMap<Float, SoftReference<ImageIcon>>(SCALED_ICONS_CACHE_LIMIT) {
        @Override
        public boolean removeEldestEntry(Map.Entry<Float, SoftReference<ImageIcon>> entry) {
          return size() > SCALED_ICONS_CACHE_LIMIT;
        }
      });

      /**
       * Retrieves the orig image (1x, 2x) based on the pixScale.
       */
      private Image getOrLoadOrigImage(boolean needRetinaImage) {
        Image image = SoftReference.dereference(origImagesCache.get(needRetinaImage));
        if (image != null) return image;

        image = ImageLoader.loadFromUrl(myUrl, false, myFilters, needRetinaImage ? 2f : 1f);
        if (image == null) return null;
        origImagesCache.put(needRetinaImage, new SoftReference<Image>(image));
        return image;
      }

      /**
       * Retrieves the orig icon based on the pixScale, then scale it by the instanceScale.
       */
      public ImageIcon getOrScaleIcon(float pixScale, float instanceScale) {
        final float effectiveScale = pixScale * instanceScale;
        ImageIcon icon = SoftReference.dereference(scaledIconsCache.get(effectiveScale));
        if (icon != null) {
          return icon;
        }

        Image image;
        if (svg) {
          image = doWithTmpRegValue("ide.svg.icon", true, new Callable<Image>() {
            @Override
            public Image call() {
              return ImageLoader.loadFromUrl(myUrl, true, myFilters, effectiveScale);
            }
          });
        }
        else {
          boolean needRetinaImage = JBUI.isHiDPI(effectiveScale);
          image = getOrLoadOrigImage(needRetinaImage);
          if (image == null) return null;

          if (!UIUtil.isJreHiDPIEnabled() && needRetinaImage) {
            instanceScale = effectiveScale / 2f; // the image is 2x raw BufferedImage, compensate it
          }

          image = ImageUtil.scaleImage(image, instanceScale);
        }
        icon = checkIcon(image, myUrl);
        if (icon != null && (icon.getIconWidth() * icon.getIconHeight() * 4) < ImageLoader.CACHED_IMAGE_MAX_SIZE) {
          scaledIconsCache.put(effectiveScale, new SoftReference<ImageIcon>(icon));
        }
        return icon;
      }

      /**
       * Retrieves the orig icon based on the pixScale.
       */
      public ImageIcon getOrLoadIcon(float pixScale) {
        return getOrScaleIcon(pixScale, 1f);
      }

      public void clear() {
        scaledIconsCache.clear();
        origImagesCache.clear();
      }
    }
  }

  public abstract static class LazyIcon extends JBUI.UpdatingJBIcon {
    private boolean myWasComputed;
    private Icon myIcon;
    private boolean isDarkVariant = USE_DARK_ICONS;
    private int numberOfPatchers = ourPatchers.size();
    private ImageFilter filter = IMAGE_FILTER;

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      final Icon icon = getOrComputeIcon(g);
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
      return getOrComputeIcon(null);
    }

    protected final synchronized Icon getOrComputeIcon(@Nullable Graphics g) {
      if (!myWasComputed || isDarkVariant != USE_DARK_ICONS || needUpdateJBUIScale((Graphics2D)g) || filter != IMAGE_FILTER || numberOfPatchers != ourPatchers.size()) {
        isDarkVariant = USE_DARK_ICONS;
        updateJBUIScale((Graphics2D)g);
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

    public Icon inOriginalScale() {
      Icon icon = getOrComputeIcon();
      if (icon != null) {
        if (icon instanceof CachedImageIcon) {
          Image img = ((CachedImageIcon)icon).myScaledIconsCache.getOrLoadOrigImage(false);
          if (img != null) {
            icon = new ImageIcon(img);
          }
        }
      }
      return icon;
    }
  }

  private static class LabelHolder {
    /**
     * To get disabled icon with paint it into the image. Some icons require
     * not null component to paint.
     */
    private static final JComponent ourFakeComponent = new JLabel();
  }

  /**
   * Do something with the temporarily registry value.
   */
  private static <T> T doWithTmpRegValue(String key, Boolean tempValue, Callable<T> action) {
    RegistryValue regVal = Registry.get(key);
    boolean regValOrig = regVal.asBoolean();
    regVal.setValue(tempValue);
    try {
      return action.call();
    }
    catch (Exception ignore) {
      return null;
    }
    finally {
      regVal.setValue(regValOrig);
    }
  }
}
