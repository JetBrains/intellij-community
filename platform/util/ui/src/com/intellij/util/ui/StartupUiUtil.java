// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.Function;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ui.accessibility.ScreenReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.io.StringReader;
import java.util.Locale;
import java.util.Map;

public final class StartupUiUtil {
  private static String ourSystemLaFClassName;
  private static volatile StyleSheet ourDefaultHtmlKitCss;

  public static final String ARIAL_FONT_NAME = "Arial";

  public static @NotNull String getSystemLookAndFeelClassName() {
    if (ourSystemLaFClassName != null) {
      return ourSystemLaFClassName;
    }

    if (SystemInfoRt.isLinux) {
      // Normally, GTK LaF is considered "system" when:
      // 1) Gnome session is run
      // 2) gtk lib is available
      // Here we weaken the requirements to only 2) and force GTK LaF
      // installation in order to let it properly scale default font
      // based on Xft.dpi value.
      try {
        @SuppressWarnings("SpellCheckingInspection")
        String name = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
        Class<?> cls = Class.forName(name);
        LookAndFeel laf = (LookAndFeel)cls.getDeclaredConstructor().newInstance();
        // if gtk lib is available
        if (laf.isSupportedLookAndFeel()) {
          ourSystemLaFClassName = name;
          return ourSystemLaFClassName;
        }
      }
      catch (Exception ignore) {
      }
    }

    ourSystemLaFClassName = UIManager.getSystemLookAndFeelClassName();
    return ourSystemLaFClassName;
  }

  public static void initDefaultLaF()
    throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {

    blockATKWrapper();

    Activity activity = StartUpMeasurer.startActivity("LaF initialization");
    UIManager.setLookAndFeel(getSystemLookAndFeelClassName());
    activity.end();
  }

  public static void configureHtmlKitStylesheet() {
    if (ourDefaultHtmlKitCss != null) {
      return;
    }

    Activity activity = StartUpMeasurer.startActivity("html kit configuration");

    // save the default JRE CSS and ..
    HTMLEditorKit kit = new HTMLEditorKit();
    ourDefaultHtmlKitCss = kit.getStyleSheet();
    // .. erase global ref to this CSS so no one can alter it
    kit.setStyleSheet(null);

    // Applied to all JLabel instances, including subclasses. Supported in JBR only.
    UIManager.getDefaults().put("javax.swing.JLabel.userStyleSheet", JBHtmlEditorKit.createStyleSheet());
    activity.end();
  }

  public static @NotNull StyleSheet createStyleSheet(@NotNull String css) {
    StyleSheet styleSheet = new StyleSheet();
    try {
      styleSheet.loadRules(new StringReader(css), null);
    }
    catch (IOException e) {
      throw new RuntimeException(e); // shouldn't happen
    }
    return styleSheet;
  }

  public static boolean isUnderDarcula() {
    return UIManager.getLookAndFeel().getName().contains("Darcula");
  }

  @ApiStatus.Internal
  public static int doGetLcdContrastValueForSplash(boolean isUnderDarcula) {
    if (SystemInfoRt.isMac) {
      return isUnderDarcula ? 140 : 230;
    }
    else {
      @SuppressWarnings({"unchecked", "SpellCheckingInspection"})
      Map<Object, Object> map = (Map<Object, Object>)Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
      if (map == null) {
        return 140;
      }
      else {
        Object o = map.get(RenderingHints.KEY_TEXT_LCD_CONTRAST);
        if (o == null) {
          return 140;
        }
        else {
          int lcdContrastValue = (Integer)o;
          return normalizeLcdContrastValue(lcdContrastValue);
        }
      }
    }
  }

  static int normalizeLcdContrastValue(int lcdContrastValue) {
    return (lcdContrastValue < 100 || lcdContrastValue > 250) ? 140 : lcdContrastValue;
  }

  /*
   * The method should be called before java.awt.Toolkit.initAssistiveTechnologies()
   * which is called from Toolkit.getDefaultToolkit().
   */
  private static void blockATKWrapper() {
    // registry must be not used here, because this method called before application loading
    //noinspection SpellCheckingInspection
    if (!SystemInfoRt.isLinux || !Boolean.parseBoolean(System.getProperty("linux.jdk.accessibility.atkwrapper.block", "true"))) {
      return;
    }

    if (ScreenReader.isEnabled(ScreenReader.ATK_WRAPPER)) {
      // Replace AtkWrapper with a dummy Object. It'll be instantiated & GC'ed right away, a NOP.
      System.setProperty("javax.accessibility.assistive_technologies", "java.lang.Object");
      Logger.getInstance(StartupUiUtil.class).info(ScreenReader.ATK_WRAPPER + " is blocked, see IDEA-149219");
    }
  }

  static StyleSheet getDefaultHtmlKitCss() {
    return ourDefaultHtmlKitCss;
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the default monitor device is HiDPI.
   * (analogue of {@link UIUtil#isRetina()} on macOS)
   */
  public static boolean isJreHiDPI() {
    return JreHiDpiUtil.isJreHiDPI((GraphicsConfiguration)null);
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the provided component is tied to a HiDPI device.
   */
  public static boolean isJreHiDPI(@Nullable Component comp) {
    GraphicsConfiguration gc = comp != null ? comp.getGraphicsConfiguration() : null;
    return JreHiDpiUtil.isJreHiDPI(gc);
  }

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the provided system scale context is HiDPI.
   */
  public static boolean isJreHiDPI(@Nullable ScaleContext ctx) {
    return JreHiDpiUtil.isJreHiDPIEnabled() && JBUIScale.isHiDPI(JBUIScale.sysScale(ctx));
  }

  public static @NotNull Point getCenterPoint(@NotNull Dimension container, @NotNull Dimension child) {
    return getCenterPoint(new Rectangle(container), child);
  }

  public static @NotNull Point getCenterPoint(@NotNull Rectangle container, @NotNull Dimension child) {
    return new Point(
      container.x + (container.width - child.width) / 2,
      container.y + (container.height - child.height) / 2
    );
  }

  /**
   * A hidpi-aware wrapper over {@link Graphics#drawImage(Image, int, int, ImageObserver)}.
   *
   * @see #drawImage(Graphics, Image, Rectangle, Rectangle, ImageObserver)
   */
  public static void drawImage(@NotNull Graphics g, @NotNull Image image, int x, int y, @Nullable ImageObserver observer) {
    drawImage(g, image, new Rectangle(x, y, -1, -1), null, null, observer);
  }

  static void drawImage(@NotNull Graphics g, @NotNull Image image, int x, int y, int width, int height, @Nullable BufferedImageOp op, ImageObserver observer) {
    Rectangle srcBounds = width >= 0 && height >= 0 ? new Rectangle(x, y, width, height) : null;
    drawImage(g, image, new Rectangle(x, y, width, height), srcBounds, op, observer);
  }

  /**
   * A hidpi-aware wrapper over {@link Graphics#drawImage(Image, int, int, int, int, ImageObserver)}.
   *
   * @see #drawImage(Graphics, Image, Rectangle, Rectangle, BufferedImageOp, ImageObserver)
   */
  public static void drawImage(@NotNull Graphics g, @NotNull Image image, @Nullable Rectangle dstBounds, @Nullable ImageObserver observer) {
    drawImage(g, image, dstBounds, null, null, observer);
  }

  /**
   * @see #drawImage(Graphics, Image, Rectangle, Rectangle, BufferedImageOp, ImageObserver)
   */
  public static void drawImage(@NotNull Graphics g,
                               @NotNull Image image,
                               @Nullable Rectangle dstBounds,
                               @Nullable Rectangle srcBounds,
                               @Nullable ImageObserver observer) {
    drawImage(g, image, dstBounds, srcBounds, null, observer);
  }

  /**
   * A hidpi-aware wrapper over {@link Graphics#drawImage(Image, int, int, int, int, int, int, int, int, ImageObserver)}.
   * <p>
   * The {@code dstBounds} and {@code srcBounds} are in the user space (just like the width/height of the image).
   * If {@code dstBounds} is null or if its width/height is set to (-1) the image bounds or the image width/height is used.
   * If {@code srcBounds} is null or if its width/height is set to (-1) the image bounds or the image right/bottom area to the provided x/y is used.
   */
  public static void drawImage(@NotNull Graphics g,
                               @NotNull Image image,
                               @Nullable Rectangle dstBounds,
                               @Nullable Rectangle srcBounds,
                               @Nullable BufferedImageOp op,
                               @Nullable ImageObserver observer) {
    int userWidth = ImageUtil.getUserWidth(image);
    int userHeight = ImageUtil.getUserHeight(image);

    int dx = 0;
    int dy = 0;
    int dw = -1;
    int dh = -1;
    if (dstBounds != null) {
      dx = dstBounds.x;
      dy = dstBounds.y;
      dw = dstBounds.width;
      dh = dstBounds.height;
    }
    boolean hasDstSize = dw >= 0 && dh >= 0;

    Graphics2D invG = null;
    double scale = 1;
    if (image instanceof JBHiDPIScaledImage) {
      JBHiDPIScaledImage hidpiImage = (JBHiDPIScaledImage)image;
      Image delegate = hidpiImage.getDelegate();
      if (delegate != null) image = delegate;
      scale = hidpiImage.getScale();

      AffineTransform tx = ((Graphics2D)g).getTransform();
      if (scale == tx.getScaleX()) {
        // The image has the same original scale as the graphics scale. However, the real image
        // scale - userSize/realSize - can suffer from inaccuracy due to the image user size
        // rounding to int (userSize = (int)realSize/originalImageScale). This may case quality
        // loss if the image is drawn via Graphics.drawImage(image, <srcRect>, <dstRect>)
        // due to scaling in Graphics. To avoid that, the image should be drawn directly via
        // Graphics.drawImage(image, 0, 0) on the unscaled Graphics.
        double gScaleX = tx.getScaleX();
        double gScaleY = tx.getScaleY();
        tx.scale(1 / gScaleX, 1 / gScaleY);
        tx.translate(dx * gScaleX, dy * gScaleY);
        dx = dy = 0;
        g = invG = (Graphics2D)g.create();
        invG.setTransform(tx);
      }
    }
    final double _scale = scale;
    Function<Integer, Integer> size = size1 -> (int)Math.round(size1 * _scale);
    try {
      if (op != null && image instanceof BufferedImage) {
        image = op.filter((BufferedImage)image, null);
      }
      if (invG != null && hasDstSize) {
        dw = size.fun(dw);
        dh = size.fun(dh);
      }
      if (srcBounds != null) {
        int sx = size.fun(srcBounds.x);
        int sy = size.fun(srcBounds.y);
        int sw = srcBounds.width >= 0 ? size.fun(srcBounds.width) : size.fun(userWidth) - sx;
        int sh = srcBounds.height >= 0 ? size.fun(srcBounds.height) : size.fun(userHeight) - sy;
        if (!hasDstSize) {
          dw = size.fun(userWidth);
          dh = size.fun(userHeight);
        }
        g.drawImage(image,
                    dx, dy, dx + dw, dy + dh,
                    sx, sy, sx + sw, sy + sh,
                    observer);
      }
      else if (hasDstSize) {
        g.drawImage(image, dx, dy, dw, dh, observer);
      }
      else if (invG == null) {
        g.drawImage(image, dx, dy, userWidth, userHeight, observer);
      }
      else {
        g.drawImage(image, dx, dy, observer);
      }
    }
    finally {
      if (invG != null) invG.dispose();
    }
  }

  /**
   * @see #drawImage(Graphics, Image, int, int, ImageObserver)
   */
  public static void drawImage(@NotNull Graphics g, @NotNull BufferedImage image, @Nullable BufferedImageOp op, int x, int y) {
    drawImage(g, image, x, y, -1, -1, op, null);
  }

  public static Font getLabelFont() {
    return UIManager.getFont("Label.font");
  }

  public static boolean isDialogFont(@NotNull Font font) {
    return Font.DIALOG.equals(font.getFamily(Locale.US));
  }
}
