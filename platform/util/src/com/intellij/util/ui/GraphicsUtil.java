/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.util.MethodInvocator;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class GraphicsUtil {
  private static final MethodInvocator ourSafelyGetGraphicsMethod = new MethodInvocator(JComponent.class, "safelyGetGraphics", Component.class);

  @SuppressWarnings("UndesirableClassUsage")
  private static final Graphics2D ourGraphics = new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB).createGraphics();
  static {
    setupFractionalMetrics(ourGraphics);
    setupAntialiasing(ourGraphics, true, true);
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

  public static int charsWidth(char[] data, int off, int len, Font font) {
    return ourGraphics.getFontMetrics(font).charsWidth(data, off, len);
  }

  public static int charWidth(char ch,Font font) {
    return ourGraphics.getFontMetrics(font).charWidth(ch);
  }

  public static int charWidth(int ch,Font font) {
    return ourGraphics.getFontMetrics(font).charWidth(ch);
  }

  public static void setupAntialiasing(Graphics g2, boolean enableAA, boolean ignoreSystemSettings) {
    if (g2 instanceof Graphics2D) {
      Graphics2D g = (Graphics2D)g2;
      Toolkit tk = Toolkit.getDefaultToolkit();
      //noinspection HardCodedStringLiteral
      Map map = (Map)tk.getDesktopProperty("awt.font.desktophints");

      if (map != null && !ignoreSystemSettings) {
        g.addRenderingHints(map);
      } else {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           enableAA ? RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
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

  /**
   * Invoking {@link Component#getGraphics()} disables true double buffering withing {@link JRootPane},
   * even if no subsequent drawing is actually performed.
   * <p>
   * This matters only if we use the default {@link RepaintManager} and {@code swing.bufferPerWindow = true}.
   * <p>
   * True double buffering is needed to eliminate tearing on blit-accelerated scrolling and to restore
   * frame buffer content without the usual repainting, even when the EDT is blocked.
   * <p>
   * As a rule of thumb, you should never invoke neither {@link Component#getGraphics()}
   * nor {@link GraphicsUtil#safelyGetGraphics(Component)} unless you really need to perform some drawing.
   * <p>
   * Under the hood, "getGraphics" is actually "createGraphics" - it creates a new object instance and allocates native resources,
   * that should be subsequently released by calling {@link Graphics#dispose()} (called from {@link Graphics#finalize()},
   * but there's no need to retain resources unnecessarily).
   * <p>
   * If you need {@link GraphicsConfiguration}, rely on {@link Component#getGraphicsConfiguration()},
   * instead of {@link Graphics2D#getDeviceConfiguration()}.
   * <p>
   * If you absolutely have to acquire an instance of {@link Graphics}, do that via {@link GraphicsUtil#safelyGetGraphics(Component)}
   * and don't forget to invoke {@link Graphics#dispose()} afterwards.
   *
   * @see JRootPane#disableTrueDoubleBuffering()
   * @see JBViewport#isTrueDoubleBufferingAvailableFor(JComponent)
   */
  public static Graphics safelyGetGraphics(Component c) {
    return SystemProperties.isTrueSmoothScrollingEnabled() && ourSafelyGetGraphicsMethod.isAvailable()
           ? (Graphics)ourSafelyGetGraphicsMethod.invoke(null, c)
           : c.getGraphics();
  }
}
