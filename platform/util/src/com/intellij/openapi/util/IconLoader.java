/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ImageLoader;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.RetinaImage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public final class IconLoader {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.IconLoader");
  public static boolean STRICT = false;
  private static boolean USE_DARK_ICONS = UIUtil.isUnderDarcula();

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private static final ConcurrentMap<URL, CachedImageIcon> ourIconsCache = ContainerUtil.newConcurrentMap(100, 0.9f, 2);

  /**
   * This cache contains mapping between icons and disabled icons.
   */
  private static final Map<Icon, Icon> ourIcon2DisabledIcon = new WeakHashMap<Icon, Icon>(200);
  @NonNls private static final Map<String, String> ourDeprecatedIconsReplacements = new HashMap<String, String>();

  static {
    ourDeprecatedIconsReplacements.put("/general/toolWindowDebugger.png", "AllIcons.Toolwindows.ToolWindowDebugger");
    ourDeprecatedIconsReplacements.put("/general/toolWindowChanges.png", "AllIcons.Toolwindows.ToolWindowChanges");

    ourDeprecatedIconsReplacements.put("/actions/showSettings.png", "AllIcons.General.ProjectSettings");
    ourDeprecatedIconsReplacements.put("/general/ideOptions.png", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/general/applicationSettings.png", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/add.png", "AllIcons.General.Add");
    ourDeprecatedIconsReplacements.put("/vcs/customizeView.png", "AllIcons.General.Settings");

    ourDeprecatedIconsReplacements.put("/vcs/refresh.png", "AllIcons.Actions.Refresh");
    ourDeprecatedIconsReplacements.put("/actions/sync.png", "AllIcons.Actions.Refresh");
    ourDeprecatedIconsReplacements.put("/actions/refreshUsages.png", "AllIcons.Actions.Rerun");

    ourDeprecatedIconsReplacements.put("/compiler/error.png", "AllIcons.General.Error");
    ourDeprecatedIconsReplacements.put("/compiler/hideWarnings.png", "AllIcons.General.HideWarnings");
    ourDeprecatedIconsReplacements.put("/compiler/information.png", "AllIcons.General.Information");
    ourDeprecatedIconsReplacements.put("/compiler/warning.png", "AllIcons.General.Warning");
    ourDeprecatedIconsReplacements.put("/ide/errorSign.png", "AllIcons.General.Error");

    ourDeprecatedIconsReplacements.put("/ant/filter.png", "AllIcons.General.Filter");
    ourDeprecatedIconsReplacements.put("/inspector/useFilter.png", "AllIcons.General.Filter");

    ourDeprecatedIconsReplacements.put("/actions/showSource.png", "AllIcons.Actions.Preview");
    ourDeprecatedIconsReplacements.put("/actions/consoleHistory.png", "AllIcons.General.MessageHistory");
    ourDeprecatedIconsReplacements.put("/vcs/messageHistory.png", "AllIcons.General.MessageHistory");
  }

  private static final ImageIcon EMPTY_ICON = new ImageIcon(UIUtil.createImage(1, 1, BufferedImage.TYPE_3BYTE_BGR)) {
    @NonNls
    public String toString() {
      return "Empty icon " + super.toString();
    }
  };

  private static boolean ourIsActivated = false;

  private IconLoader() { }

  @Deprecated
  public static Icon getIcon(@NotNull final Image image) {
    return new MyImageIcon(image);
  }

  public static void setUseDarkIcons(boolean useDarkIcons) {
    USE_DARK_ICONS = useDarkIcons;
    ourIconsCache.clear();
    ourIcon2DisabledIcon.clear();
  }


  //TODO[kb] support iconsets
  //public static Icon getIcon(@NotNull final String path, @NotNull final String darkVariantPath) {
  //  return new InvariantIcon(getIcon(path), getIcon(darkVariantPath));
  //}

  @NotNull
  public static Icon getIcon(@NonNls @NotNull final String path) {
    int stackFrameCount = 2;
    Class callerClass = ReflectionUtil.findCallerClass(stackFrameCount);
    while (callerClass != null && callerClass.getClassLoader() == null) { // looks like a system class
      callerClass = ReflectionUtil.findCallerClass(++stackFrameCount);
    }
    if (callerClass == null) {
      callerClass = ReflectionUtil.findCallerClass(1);
    }
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
   * Use only if you expected null return value, otherwise see {@link IconLoader#getIcon(java.lang.String)}
   */
  public static Icon findIcon(@NonNls @NotNull String path) {
    int stackFrameCount = 2;
    Class callerClass = ReflectionUtil.findCallerClass(stackFrameCount);
    while (callerClass != null && callerClass.getClassLoader() == null) { // looks like a system class
      callerClass = ReflectionUtil.findCallerClass(++stackFrameCount);
    }
    if (callerClass == null) {
      callerClass = ReflectionUtil.findCallerClass(1);
    }
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
   * Use only if you expected null return value, otherwise see {@link IconLoader#getIcon(java.lang.String, java.lang.Class)}
   */
  @Nullable
  public static Icon findIcon(@NotNull final String path, @NotNull final Class aClass) {
    return findIcon(path, aClass, false);
  }

  @Nullable
  public static Icon findIcon(@NotNull String path, @NotNull final Class aClass, boolean computeNow) {
    path = undeprecate(path);
    if (isReflectivePath(path)) return getReflectiveIcon(path, aClass.getClassLoader());

    URL myURL = aClass.getResource(path);
    if (myURL == null) {
      if (STRICT) throw new RuntimeException("Can't find icon in '" + path + "' near "+aClass);
      return null;
    }
    return findIcon(myURL);
  }

  @NotNull
  private static String undeprecate(@NotNull String path) {
    String replacement = ourDeprecatedIconsReplacements.get(path);
    return replacement == null ? path : replacement;
  }

  private static boolean isReflectivePath(@NotNull String path) {
    List<String> paths = StringUtil.split(path, ".");
    return paths.size() > 1 && paths.get(0).endsWith("Icons");
  }

  @Nullable
  public static Icon findIcon(URL url) {
    if (url == null) {
      return null;
    }
    CachedImageIcon icon = ourIconsCache.get(url);
    if (icon == null) {
      icon = new CachedImageIcon(url);
      icon = ConcurrencyUtil.cacheOrGet(ourIconsCache, url, icon);
    }
    return icon;
  }

  @Nullable
  public static Icon findIcon(@NotNull String path, @NotNull ClassLoader classLoader) {
    path = undeprecate(path);
    if (isReflectivePath(path)) return getReflectiveIcon(path, classLoader);
    if (!StringUtil.startsWithChar(path, '/')) return null;

    final URL url = classLoader.getResource(path.substring(1));
    return findIcon(url);
  }

  @Nullable
  private static Icon checkIcon(final Image image, @NotNull URL url) {
    if (image == null || image.getHeight(LabelHolder.ourFakeComponent) < 1) { // image wasn't loaded or broken
      return null;
    }

    final Icon icon = getIcon(image);
    if (icon != null && !isGoodSize(icon)) {
      LOG.error("Invalid icon: " + url); // # 22481
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

      Image img = createDisabled(image);
      if (UIUtil.isRetina()) img = RetinaImage.createFrom(img, 2, ImageLoader.ourComponent);

      disabledIcon = new MyImageIcon(img);
      ourIcon2DisabledIcon.put(icon, disabledIcon);
    }
    return disabledIcon;
  }

  private static Image createDisabled(BufferedImage image) {
    final GrayFilter filter = UIUtil.getGrayFilter();
    final ImageProducer prod = new FilteredImageSource(image.getSource(), filter);
    return Toolkit.getDefaultToolkit().createImage(prod);
  }

  public static Icon getTransparentIcon(@NotNull final Icon icon) {
    return getTransparentIcon(icon, 0.5f);
  }

  public static Icon getTransparentIcon(@NotNull final Icon icon, final float alpha) {
    return new Icon() {
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

  private static final class CachedImageIcon implements Icon {
    private Object myRealIcon;
    @NotNull
    private final URL myUrl;
    private boolean dark;

    public CachedImageIcon(@NotNull URL url) {
      myUrl = url;
      dark = USE_DARK_ICONS;
    }

    @NotNull
    private synchronized Icon getRealIcon() {
      if (isLoaderDisabled()) return EMPTY_ICON;

      if (dark != USE_DARK_ICONS) {
        myRealIcon = null;
        dark = USE_DARK_ICONS;
      }
      Object realIcon = myRealIcon;
      if (realIcon instanceof Icon) return (Icon)realIcon;

      Icon icon;
      if (realIcon instanceof Reference) {
        icon = ((Reference<Icon>)realIcon).get();
        if (icon != null) return icon;
      }

      Image image = ImageLoader.loadFromUrl(myUrl);
      icon = checkIcon(image, myUrl);

      if (icon != null) {
        if (icon.getIconWidth() < 50 && icon.getIconHeight() < 50) {
          realIcon = icon;
        }
        else {
          realIcon = new SoftReference<Icon>(icon);
        }
        myRealIcon = realIcon;
      }

      return icon == null ? EMPTY_ICON : icon;
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
  }

  private static final class MyImageIcon extends ImageIcon {
    public MyImageIcon(final Image image) {
      super(image);
    }

    @Override
    public final synchronized void paintIcon(final Component c, final Graphics g, final int x, final int y) {
      final ImageObserver observer = getImageObserver();

      UIUtil.drawImage(g, getImage(), x, y, observer == null ? c : observer);
    }
  }

  public abstract static class LazyIcon implements Icon {
    private boolean myWasComputed;
    private Icon myIcon;
    private boolean isDarkVariant = USE_DARK_ICONS;

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
      if (!myWasComputed || isDarkVariant != USE_DARK_ICONS) {
        isDarkVariant = USE_DARK_ICONS;
        myWasComputed = true;
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
