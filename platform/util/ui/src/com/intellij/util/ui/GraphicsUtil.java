// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.util.MethodInvocator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

public final class GraphicsUtil {
  @SuppressWarnings("SpellCheckingInspection")
  @ApiStatus.Internal
  public static final String DESKTOP_HINTS = "awt.font.desktophints";

  private static final MethodInvocator ourSafelyGetGraphicsMethod = new MethodInvocator(JComponent.class, "safelyGetGraphics", Component.class);

  @SuppressWarnings("UndesirableClassUsage")
  private static final Graphics2D ourGraphics = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).createGraphics();
  static {
    setupFractionalMetrics(ourGraphics);
    setupAntialiasing(ourGraphics, true, true);
  }

  /** This method is intended to use when user settings are not accessible yet.
   *  Use it to set up default RenderingHints.
   */
  public static void applyRenderingHints(@NotNull Graphics2D g) {
    Toolkit tk = Toolkit.getDefaultToolkit();
    Map<?, ?> map = (Map<?, ?>)tk.getDesktopProperty(DESKTOP_HINTS);
    if (map != null) {
      g.addRenderingHints(map);
    }
  }

  public static void setupFractionalMetrics(Graphics g) {
    ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
  }

  public static void setupAntialiasing(@NotNull Graphics g2) {
    setupAntialiasing(g2, true, false);
  }

  public static int stringWidth(@NotNull String text, Font font) {
    setupAntialiasing(ourGraphics, true, true);
    return ourGraphics.getFontMetrics(font).stringWidth(text);
  }

  public static int charWidth(char ch,Font font) {
    return ourGraphics.getFontMetrics(font).charWidth(ch);
  }

  public static int charWidth(int ch,Font font) {
    return ourGraphics.getFontMetrics(font).charWidth(ch);
  }

  public static void setupAntialiasing(Graphics g2, boolean enableAA, boolean ignoreSystemSettings) {
    if (g2 instanceof Graphics2D g) {
      Toolkit tk = Toolkit.getDefaultToolkit();
      Map<?, ?> map = (Map<?, ?>)tk.getDesktopProperty(DESKTOP_HINTS);
      if (map != null && !ignoreSystemSettings) {
        g.addRenderingHints(map);
      }
      else {
        Object hint = enableAA ? RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, hint);
      }
    }
  }

  public static GraphicsConfig setupRoundedBorderAntialiasing(Graphics g) {
    return new GraphicsConfig(g).setupRoundedBorderAntialiasing();
  }

  public static GraphicsConfig setupAAPainting(Graphics g) {
    return new GraphicsConfig(g).setupAAPainting();
  }

  public static GraphicsConfig disableAAPainting(Graphics g) {
    return new GraphicsConfig(g).disableAAPainting();
  }

  public static GraphicsConfig paintWithAlpha(Graphics g, float alpha) {
    return new GraphicsConfig(g).paintWithAlpha(alpha);
  }

  public static void paintWithAlpha(Graphics g, float alpha, @NotNull Runnable paint) {
    Graphics2D g2 = (Graphics2D)g;
    Composite oldComposite = g2.getComposite();
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    paint.run();
    g2.setComposite(oldComposite);
  }

  /**
   * <p>Invoking {@link Component#getGraphics()} disables true double buffering withing {@link JRootPane},
   * even if no subsequent drawing is actually performed.</p>
   *
   * <p>This matters only if we use the default {@link RepaintManager} and {@code swing.bufferPerWindow = true}.</p>
   *
   * <p>True double buffering is needed to eliminate tearing on blit-accelerated scrolling and to restore
   * frame buffer content without the usual repainting, even when the EDT is blocked.</p>
   *
   * <p>As a rule of thumb, you should never invoke neither {@link Component#getGraphics()}
   * nor this method unless you really need to perform some drawing.</p>
   *
   * <p>Under the hood, "getGraphics" is actually "createGraphics" - it creates a new object instance and allocates native resources,
   * that should be subsequently released by calling {@link Graphics#dispose()} (called from {@code finalize()},
   * but there's no need to retain resources unnecessarily).</p>
   *
   * <p>If you need {@link GraphicsConfiguration}, rely on {@link Component#getGraphicsConfiguration()},
   * instead of {@link Graphics2D#getDeviceConfiguration()}.</p>
   *
   * <p>If you absolutely have to acquire an instance of {@link Graphics}, do that via calling this method
   * and don't forget to invoke {@link Graphics#dispose()} afterwards.</p>
   *
   * @see JRootPane#disableTrueDoubleBuffering()
   */
  public static Graphics safelyGetGraphics(Component c) {
    return ourSafelyGetGraphicsMethod.isAvailable() ? (Graphics)ourSafelyGetGraphicsMethod.invoke(null, c) : c.getGraphics();
  }

  /**
   * Put a context hint that instructs using specified aliasing for a given component.
   * It's preferred over using {@link #setupAntialiasing(Graphics)}, as it will allow to compute {@link JComponent#getPreferredSize()}
   * without using {@link JComponent#getGraphics()} (see {@link #safelyGetGraphics(Component)} on why it shall be avoided).
   * <p>
   * NB: {@link JComponent#paint(Graphics)} should be using {@link sun.swing.SwingUtilities2#drawString} to make use of this component hint.
   */
  public static void setAntialiasingType(@NotNull JComponent component, @Nullable Object type) {
    AATextInfo.putClientProperty((AATextInfo) type, component);
  }

  public static @NotNull AATextInfo createAATextInfo(@NotNull Object hint) {
    return new AATextInfo(hint, StartupUiUtil.getLcdContrastValue());
  }

  public static boolean isRemoteEnvironment() {
    String geClassName = GraphicsEnvironment.getLocalGraphicsEnvironment().getClass().getSimpleName();
    return geClassName.equals("PGraphicsEnvironment") || geClassName.equals("IdeGraphicsEnvironment");
  }
}
