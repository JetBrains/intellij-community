// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.util.ImageLoader;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.JBUI.ScaleContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.*;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.File;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.util.ui.JBUI.ScaleType.SYS_SCALE;

/**
 * @author tav
 */
@SuppressWarnings("JUnitTestCaseWithNoTests")
public class TestScaleHelper {
  private static final String STANDALONE_PROP = "intellij.test.standalone";

  private static final Map<String, String> originalSysProps = new HashMap<>();
  private static final Map<String, String> originalRegProps = new HashMap<>();

  private static float originalUserScale;
  private static boolean originalJreHiDPIEnabled;

  @BeforeClass
  public static void setState() {
    originalUserScale = JBUI.scale(1f);
    originalJreHiDPIEnabled = UIUtil.isJreHiDPIEnabled();
  }

  @AfterClass
  public static void restoreState() {
    JBUI.setUserScaleFactor(originalUserScale);
    overrideJreHiDPIEnabled(originalJreHiDPIEnabled);
    restoreRegistryProperties();
    restoreSystemProperties();
  }

  public static void setRegistryProperty(@NotNull String key, @NotNull String value) {
    final RegistryValue prop = Registry.get(key);
    originalRegProps.put(key, prop.asString());
    prop.setValue(value);
  }

  public static void setSystemProperty(@NotNull String name, @Nullable String value) {
    originalSysProps.put(name, System.getProperty(name));
    _setProperty(name, value);
  }

  private static void _setProperty(String name, String value) {
    if (value != null) {
      System.setProperty(name, value);
    }
    else {
      System.clearProperty(name);
    }
  }

  public static void restoreSystemProperties() {
    for (Map.Entry<String, String> entry : originalSysProps.entrySet()) {
      _setProperty(entry.getKey(), entry.getValue());
    }
  }

  public static void restoreRegistryProperties() {
    for (Map.Entry<String, String> entry : originalRegProps.entrySet()) {
      Registry.get(entry.getKey()).setValue(entry.getValue());
    }
  }

  public static void overrideJreHiDPIEnabled(boolean enabled) {
    UIUtil.test_jreHiDPI().set(enabled);
  }

  public static void assumeStandalone() {
    Assume.assumeTrue("not in " + STANDALONE_PROP + " mode", SystemProperties.is(STANDALONE_PROP));
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
    double gScale = UIUtil.isJreHiDPIEnabled() ? scale : 1;
    g.scale(gScale, gScale);
    return Pair.create(image, g);
  }

  public static JComponent createComponent(ScaleContext ctx) {
    return new JComponent() {
      MyGraphicsConfiguration myGC = new MyGraphicsConfiguration(ctx.getScale(SYS_SCALE));
      @Override
      public GraphicsConfiguration getGraphicsConfiguration() {
        return myGC;
      }
    };
  }

  @SuppressWarnings("unused")
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
      Image img = ImageLoader.loadFromUrl(
        new File(path).toURI().toURL(), true, false, null, ctx);
      return ImageUtil.toBufferedImage(img);
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  private static class MyGraphicsConfiguration extends GraphicsConfiguration {
    private AffineTransform myTx;

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
