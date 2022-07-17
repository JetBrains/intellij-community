// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.scale;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.ui.JreHiDpiUtil;
import com.intellij.util.ImageLoader;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.File;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.ui.scale.ScaleType.SYS_SCALE;

/**
 * @author tav
 */
public final class TestScaleHelper {
  private static final String STANDALONE_PROP = "intellij.test.standalone";

  private static final Map<String, String> originalSysProps = new HashMap<>();
  private static final Map<String, String> originalRegProps = new HashMap<>();

  private static float originalUserScale;
  private static float originalSysScale;
  private static boolean originalJreHiDPIEnabled;

  @BeforeClass
  public static void setState() {
    originalUserScale = JBUIScale.scale(1f);
    originalSysScale = JBUIScale.sysScale();
    originalJreHiDPIEnabled = JreHiDpiUtil.isJreHiDPIEnabled();
  }

  @AfterClass
  public static void restoreState() {
    JBUIScale.setUserScaleFactor(originalUserScale);
    JBUIScale.setSystemScaleFactor(originalSysScale);
    overrideJreHiDPIEnabled(originalJreHiDPIEnabled);
    restoreRegistryProperties();
    restoreSystemProperties();
  }

  public static void setRegistryProperty(@NotNull String key, @NotNull String value) {
    RegistryValue prop = Registry.get(key);
    if (originalRegProps.get(key) == null) originalRegProps.put(key, prop.asString());
    prop.setValue(value);
  }

  public static void setSystemProperty(@NotNull String name, @Nullable String value) {
    if (originalSysProps.get(name) == null) originalSysProps.put(name, System.getProperty(name));
    SystemProperties.setProperty(name, value);
  }

  public static void restoreProperties() {
    restoreSystemProperties();
    restoreRegistryProperties();
  }

  public static void restoreSystemProperties() {
    for (Map.Entry<String, String> entry : originalSysProps.entrySet()) {
      SystemProperties.setProperty(entry.getKey(), entry.getValue());
    }
  }

  public static void restoreRegistryProperties() {
    for (Map.Entry<String, String> entry : originalRegProps.entrySet()) {
      Registry.get(entry.getKey()).setValue(entry.getValue());
    }
  }

  public static void overrideJreHiDPIEnabled(boolean enabled) {
    JreHiDpiUtil.test_jreHiDPI().set(enabled);
  }

  public static void assumeStandalone() {
    Assume.assumeTrue("not in " + STANDALONE_PROP + " mode", Boolean.getBoolean(STANDALONE_PROP));
  }

  public static void assumeHeadful() {
    Assume.assumeFalse("should not be headless", GraphicsEnvironment.isHeadless());
  }

  public static Graphics2D createGraphics(double scale) {
    //noinspection UndesirableClassUsage
    Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
    g.scale(scale, scale);
    return g;
  }

  public static Pair<BufferedImage, Graphics2D> createImageAndGraphics(double scale, int width, int height) {
    //noinspection UndesirableClassUsage
    final BufferedImage image = new BufferedImage((int)Math.ceil(width * scale), (int)Math.ceil(height * scale), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    double gScale = JreHiDpiUtil.isJreHiDPIEnabled() ? scale : 1;
    g.scale(gScale, gScale);
    return Pair.create(image, g);
  }

  public static JComponent createComponent(ScaleContext ctx) {
    return new JComponent() {
      final MyGraphicsConfiguration myGC = new MyGraphicsConfiguration(ctx.getScale(SYS_SCALE));
      @Override
      public GraphicsConfiguration getGraphicsConfiguration() {
        return myGC;
      }
    };
  }

  public static void saveImage(BufferedImage image, String path) {
    try {
      javax.imageio.ImageIO.write(image, "png", new File(path));
    } catch (java.io.IOException e) {
      e.printStackTrace();
    }
  }

  public static BufferedImage loadImage(String path) {
    return loadImage(path, ScaleContext.createIdentity());
  }

  public static BufferedImage loadImage(String path, ScaleContext ctx) {
    try {
      int flags = ImageLoader.USE_SVG | ImageLoader.ALLOW_FLOAT_SCALING;
      if (StartupUiUtil.isUnderDarcula()) {
        flags |= ImageLoader.USE_DARK;
      }
      Image img = ImageLoader.loadFromUrl(new File(path).toURI().toURL().toString(), null, flags, ctx);
      return ImageUtil.toBufferedImage(img);
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public static String msg(UserScaleContext ctx) {
    return "[JRE-HiDPI " + JreHiDpiUtil.isJreHiDPIEnabled() + "], " + ctx.toString();
  }

  private static class MyGraphicsConfiguration extends GraphicsConfiguration {
    private final AffineTransform myTx;

    protected MyGraphicsConfiguration(double scale) {
      myTx = AffineTransform.getScaleInstance(scale, scale);
    }

    @Override
    public GraphicsDevice getDevice() {
      return null;
    }

    @Override
    public ColorModel getColorModel() {
      return null;
    }

    @Override
    public ColorModel getColorModel(int transparency) {
      return null;
    }

    @Override
    public AffineTransform getDefaultTransform() {
      return myTx;
    }

    @Override
    public AffineTransform getNormalizingTransform() {
      return myTx;
    }

    @Override
    public Rectangle getBounds() {
      return new Rectangle();
    }
  }
}
