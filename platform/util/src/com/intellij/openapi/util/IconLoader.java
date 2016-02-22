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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.RetrievableIcon;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.imgscalr.Scalr;
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
import java.util.concurrent.ConcurrentMap;

public final class IconLoader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.IconLoader");
  public static boolean STRICT = false;
  private static boolean USE_DARK_ICONS = UIUtil.isUnderDarcula();
  private static float SCALE = JBUI.scale(1f);
  private static ImageFilter IMAGE_FILTER;

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static final ConcurrentMap<URL, CachedImageIcon> ourIconsCache = ContainerUtil.newConcurrentMap(100, 0.9f, 2);

  /**
   * This cache contains mapping between icons and disabled icons.
   */
  private static final Map<Icon, Icon> ourIcon2DisabledIcon = new WeakHashMap<Icon, Icon>(200);
  @NonNls private static final List<IconPathPatcher> ourPatchers = new ArrayList<IconPathPatcher>(2);

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

  public static void setScale(float scale) {
    if (scale != SCALE) {
      SCALE = scale;
      clearCache();
    }
  }

  public static void setFilter(ImageFilter filter) {
    if (!Registry.is("color.blindness.icon.filter")) {
      filter = null;
    }
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

  @Nullable
  /**
   * Might return null if icon was not found.
   * Use only if you expected null return value, otherwise see {@link IconLoader#getIcon(String)}
   */
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
  public static Icon findIcon(@NotNull String path, @NotNull final Class aClass, boolean computeNow, boolean strict) {
    String originalPath = path;
    path = patchPath(path);
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

  private static String patchPath(@NotNull String path) {
    for (IconPathPatcher patcher : ourPatchers) {
      String newPath = patcher.patchPath(path);
      if (newPath != null) {
        path = newPath;
      }
    }
    return path;
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
    path = patchPath(path);
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
  private static ImageIcon checkIcon(final Image image, @NotNull URL url) {
    if (image == null || image.getHeight(LabelHolder.ourFakeComponent) < 1) { // image wasn't loaded or broken
      return null;
    }

    final Icon icon = getIcon(image);
    if (icon != null && !isGoodSize(icon)) {
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
   * @return <code>ImageIcon</code> constructed from disabled image of passed icon.
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
      final int scale = UIUtil.isRetina() ? 2 : 1;
      @SuppressWarnings("UndesirableClassUsage")
      BufferedImage image = new BufferedImage(scale*icon.getIconWidth(), scale*icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
      final Graphics2D graphics = image.createGraphics();

      graphics.setColor(UIUtil.TRANSPARENT_COLOR);
      graphics.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
      graphics.scale(scale, scale);
      icon.paintIcon(LabelHolder.ourFakeComponent, graphics, 0, 0);

      graphics.dispose();

      Image img = ImageUtil.filter(image, UIUtil.getGrayFilter());
      if (UIUtil.isRetina()) img = RetinaImage.createFrom(img);

      disabledIcon = new JBImageIcon(img);
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
   * {@link IconLoader#setScale(float)}, {@link IconLoader#setFilter(ImageFilter)}, {@link IconLoader#setUseDarkIcons(boolean)}
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

  private static final class CachedImageIcon implements ScalableIcon {
    private volatile Object myRealIcon;
    private String myOriginalPath;
    private ClassLoader myClassLoader;
    @NotNull
    private URL myUrl;
    private volatile boolean dark;
    private volatile float scale;
    private volatile int numberOfPatchers = ourPatchers.size();

    private volatile ImageFilter filter;
    private final MyScaledIconsCache myScaledIconsCache = new MyScaledIconsCache();

    public CachedImageIcon(@NotNull URL url) {
      myUrl = url;
      dark = USE_DARK_ICONS;
      scale = SCALE;
      filter = IMAGE_FILTER;
    }

    @NotNull
    private synchronized ImageIcon getRealIcon() {
      if (isLoaderDisabled() && (myRealIcon == null || dark != USE_DARK_ICONS || scale != SCALE || filter != IMAGE_FILTER || numberOfPatchers != ourPatchers.size())) return EMPTY_ICON;

      if (!isValid()) {
        myRealIcon = null;
        dark = USE_DARK_ICONS;
        scale = SCALE;
        filter = IMAGE_FILTER;
        myScaledIconsCache.clear();
        if (numberOfPatchers != ourPatchers.size()) {
          numberOfPatchers = ourPatchers.size();
          String path = myOriginalPath == null ? null : patchPath(myOriginalPath);
          if (myClassLoader != null&& path != null && path.startsWith("/")) {
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
        if (icon != null) return (ImageIcon)icon;
      }

      Image image = ImageLoader.loadFromUrl(myUrl, true, filter);
      icon = checkIcon(image, myUrl);

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
      return dark == USE_DARK_ICONS && scale == SCALE && filter == IMAGE_FILTER && numberOfPatchers == ourPatchers.size();
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      getRealIcon().paintIcon(c, g, x, y);
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
    public Icon scale(float scaleFactor) {
      if (scaleFactor == 1f) {
        return this;
      }

      if (!isValid()) getRealIcon(); // force state update & cache reset

      Icon icon = myScaledIconsCache.getScaledIcon(scaleFactor);
      if (icon != null) {
        return icon;
      }
      return this;
    }

    private class MyScaledIconsCache {
      // Map {false -> image}, {true -> image@2x}
      private Map<Boolean, SoftReference<Image>> origImagesCache = Collections.synchronizedMap(new HashMap<Boolean, SoftReference<Image>>(2));

      private static final int SCALED_ICONS_CACHE_LIMIT = 5;

      // Map {effective scale -> icon}
      private Map<Float, SoftReference<Icon>> scaledIconsCache = Collections.synchronizedMap(new LinkedHashMap<Float, SoftReference<Icon>>(SCALED_ICONS_CACHE_LIMIT) {
        @Override
        public boolean removeEldestEntry(Map.Entry<Float, SoftReference<Icon>> entry) {
          return size() > SCALED_ICONS_CACHE_LIMIT;
        }
      });

      public Image getOrigImage(boolean retina) {
        Image img = SoftReference.dereference(origImagesCache.get(retina));

        if (img == null) {
          img = ImageLoader.loadFromUrl(myUrl, UIUtil.isUnderDarcula(), retina, filter);
          origImagesCache.put(retina, new SoftReference<Image>(img));
        }
        return img;
      }

      public Icon getScaledIcon(float scale) {
        float effectiveScale = scale * JBUI.scale(1f);
        Icon icon = SoftReference.dereference(scaledIconsCache.get(effectiveScale));

        if (icon == null) {
          boolean needRetinaImage = (effectiveScale >= 1.5f || UIUtil.isRetina());
          Image image = getOrigImage(needRetinaImage);

          if (image != null) {
            Image iconImage = getRealIcon().getImage();
            int width = (int)(ImageUtil.getRealWidth(iconImage) * scale);
            int height = (int)(ImageUtil.getRealHeight(iconImage) * scale);

            Image resizedImage = Scalr.resize(ImageUtil.toBufferedImage(image), Scalr.Method.ULTRA_QUALITY, width, height);
            if (UIUtil.isRetina()) resizedImage = RetinaImage.createFrom(resizedImage);

            icon = getIcon(resizedImage);
            scaledIconsCache.put(effectiveScale, new SoftReference<Icon>(icon));
          }
        }
        return icon;
      }

      public void clear() {
        scaledIconsCache.clear();
        origImagesCache.clear();
      }
    }
  }

  public abstract static class LazyIcon implements Icon {
    private boolean myWasComputed;
    private Icon myIcon;
    private boolean isDarkVariant = USE_DARK_ICONS;
    private float scale = SCALE;
    private int numberOfPatchers = ourPatchers.size();
    private ImageFilter filter = IMAGE_FILTER;

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
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
      if (!myWasComputed || isDarkVariant != USE_DARK_ICONS || scale != SCALE || filter != IMAGE_FILTER || numberOfPatchers != ourPatchers.size()) {
        isDarkVariant = USE_DARK_ICONS;
        scale = SCALE;
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
  }

  private static class LabelHolder {
    /**
     * To get disabled icon with paint it into the image. Some icons require
     * not null component to paint.
     */
    private static final JComponent ourFakeComponent = new JLabel();
  }
}
