// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.platform.ProjectSelfieUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.math.BigInteger;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.Security;
import java.util.concurrent.ForkJoinPool;

/**
 * To customize your IDE splash go to YourIdeNameApplicationInfo.xml and edit 'logo' tag. For more information see documentation for
 * the tag attributes in ApplicationInfo.xsd file.
 */
public final class Splash extends Dialog {
  private final @Nullable ProgressSlidePainter progressSlidePainter;
  private final Image image;

  public Splash(@NotNull ApplicationInfoEx info) {
    super((Frame)null, "splash" /* not visible, but available through window properties on Linux */);
    setUndecorated(true);
    setBackground(Gray.TRANSPARENT);
    setResizable(false); // makes tiling window managers on Linux show window as floating

    progressSlidePainter = info.getProgressSlides().isEmpty() ? null : new ProgressSlidePainter(info);

    setFocusableWindowState(false);

    image = loadImage(info.getSplashImageUrl(), info);
    int width = image.getWidth(null);
    int height = image.getHeight(null);
    setSize(new Dimension(width, height));
    setLocationInTheCenterOfScreen(this);
  }

  public void initAndShow(boolean visible) {
    if (progressSlidePainter != null) {
      progressSlidePainter.startPreloading();
    }
    StartUpMeasurer.addInstantEvent("splash shown");
    Activity activity = StartUpMeasurer.startActivity("splash set visible");
    setVisible(visible);
    activity.end();
    if (visible) {
      paint(getGraphics());
      toFront();
    }
  }

  private static @NotNull Image loadImage(@NotNull String path, @NotNull ApplicationInfoEx appInfo) {
    float sysScale = JBUIScale.sysScale();
    var file = getCacheFile(path, sysScale, appInfo);
    if (file != null) {
      var image = loadImageFromCache(file);
      if (image != null) {
        return image;
      }
    }

    Image result = doLoadImage(path, sysScale);
    if (result == null) {
      throw new IllegalStateException("Cannot find image: " + path);
    }

    if (file != null) {
      ForkJoinPool.commonPool().execute(() -> {
        try {
          BufferedImage rawImage =
            (BufferedImage)(result instanceof JBHiDPIScaledImage ? ((JBHiDPIScaledImage)result).getDelegate() : result);
          assert rawImage != null;
          ProjectSelfieUtil.INSTANCE.writeImage(file, rawImage, sysScale);
        }
        catch (Throwable e) {
          Logger.getInstance(Splash.class).warn("Cannot save splash image", e);
        }
      });
    }
    return result;
  }

  static @Nullable Image doLoadImage(@NotNull String path, float sysScale) {
    BufferedImage originalImage = ImageLoader.loadImageForStartUp(path, Splash.class.getClassLoader());
    if (originalImage == null) {
      return null;
    }

    int w = originalImage.getWidth();
    int h = originalImage.getHeight();

    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage resultImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = resultImage.createGraphics();

    g2.setComposite(AlphaComposite.Src);
    ImageUtil.applyQualityRenderingHints(g2);
    //noinspection UseJBColor
    g2.setColor(Color.WHITE);
    float cornerRadius = 8 * sysScale;
    g2.fill(new RoundRectangle2D.Float(0, 0, w, h, cornerRadius, cornerRadius));
    g2.setComposite(AlphaComposite.SrcIn);
    g2.drawImage(originalImage, 0, 0, null);
    g2.dispose();
    return ImageUtil.ensureHiDPI(resultImage, ScaleContext.create());
  }

  @Override
  public void paint(Graphics g) {
    if (progressSlidePainter == null) {
      StartupUiUtil.drawImage(g, image, 0, 0, null);
    }
    else {
      paintProgress(g);
    }
  }

  private static void setLocationInTheCenterOfScreen(@NotNull Window window) {
    GraphicsConfiguration graphicsConfiguration = window.getGraphicsConfiguration();
    Rectangle bounds = graphicsConfiguration.getBounds();
    if (SystemInfoRt.isWindows) {
      JBInsets.removeFrom(bounds, ScreenUtil.getScreenInsets(graphicsConfiguration));
    }
    window.setLocation(StartupUiUtil.getCenterPoint(bounds, window.getSize()));
  }

  private void paintProgress(@Nullable Graphics g) {
    if (g == null) {
      return;
    }

    if (progressSlidePainter != null) {
      progressSlidePainter.paintSlides(g, 0.0);
    }
  }

  private static @Nullable Image loadImageFromCache(@NotNull Path file) {
    try {
      return ProjectSelfieUtil.INSTANCE.readImage(file, ScaleContext::create);
    }
    catch (Exception e) {
      // don't use `error`, because it can crash application
      Logger.getInstance(Splash.class).warn("Failed to load splash image", e);
    }
    return null;
  }

  private static @Nullable Path getCacheFile(@NotNull String path, float scale, @NotNull ApplicationInfoEx appInfo) {
    try {
      var d = MessageDigest.getInstance("SHA-256", Security.getProvider("SUN"));
      d.update(path.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE + 1).order(ByteOrder.LITTLE_ENDIAN);
      // path for EAP and release builds is the same, but content maybe different
      buffer.put((byte)(appInfo.isEAP() ? 1 : 0));

      // for dev run build data is equal to run time
      if (appInfo.getBuild().isSnapshot()) {
        long size = 0;
        try {
          String pathToSplash = path.startsWith("/") ? path.substring(1) : path;
          URL resource = Splash.class.getClassLoader().getResource(pathToSplash);
          if (resource != null) {
            size = Files.size(Path.of(resource.toURI()));
          }
        }
        catch (Exception e) {
          Logger.getInstance(Splash.class).warn("Failed to read splash image", e);
        }
        buffer.putLong(size);
      }
      else {
        buffer.putLong(appInfo.getBuildDate().getTimeInMillis());
      }
      buffer.flip();
      d.update(buffer);

      var encodedDigest = new BigInteger(1, d.digest()).toString(Character.MAX_RADIX);
      return Path.of(PathManager.getSystemPath(), "splash", encodedDigest + '.' + scale + ".ij");
    }
    catch (Exception e) {
      return null;
    }
  }
}
