// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Base64;
import java.util.Objects;

/**
 * To customize your IDE splash go to YourIdeNameApplicationInfo.xml and edit 'logo' tag. For more information see documentation for
 * the tag attributes in ApplicationInfo.xsd file.
 */
public final class Splash extends Dialog {
  private static final float JBUI_INIT_SCALE = JBUIScale.scale(1f);

  private final int myWidth;
  private final int myHeight;
  private final int progressHeight;
  private final int progressY;
  private double progress;
  private final Color progressColor;
  private int progressLastPosition = 0;
  private final Icon progressTail;
  private final @Nullable ProgressSlidePainter progressSlidePainter;
  private final Image image;

  public Splash(@NotNull ApplicationInfoEx info) {
    super((Frame)null);
    setUndecorated(true);

    progressSlidePainter = info.getProgressSlides().isEmpty() ? null : new ProgressSlidePainter(info);
    progressHeight = uiScale(info.getProgressHeight());
    progressY = uiScale(info.getProgressY());
    progressTail = getProgressTailIcon(info);

    setFocusableWindowState(false);

    image = loadImage(info.getSplashImageUrl(), info);
    myWidth = image.getWidth(null);
    myHeight = image.getHeight(null);
    long rgba = info.getProgressColor();
    //noinspection UseJBColor
    progressColor = rgba == -1 ? null : new Color((int)rgba, rgba > 0xffffff);

    setSize(new Dimension(myWidth, myHeight));
    setLocationInTheCenterOfScreen(this);
  }

  private static @Nullable Icon getProgressTailIcon(@NotNull ApplicationInfoEx info) {
    String progressTailIconName = info.getProgressTailIcon();
    if (progressTailIconName == null) {
      return null;
    }

    try {
      String path = Objects.requireNonNull(Splash.class.getResource(progressTailIconName)).toString();
      Image image = doLoadImage(path);
      return image == null ? null : new JBImageIcon(image);
    }
    catch (Exception ignore) {
      return null;
    }
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
    float scale = JBUIScale.sysScale();
    if (isCacheNeeded(scale)) {
      var image = loadImageFromCache(path, scale, appInfo);
      if (image != null) {
        return image;
      }

      cacheAsync(path, appInfo);
    }

    Image result = doLoadImage(path);
    if (result == null) {
      throw new IllegalStateException("Cannot find image: " + path);
    }
    return result;
  }

  private static void cacheAsync(@NotNull String url, @NotNull ApplicationInfoEx appInfo) {
    // Don't use already loaded image to avoid oom
    NonUrgentExecutor.getInstance().execute(() -> {
      var cacheFile = getCacheFile(url, JBUIScale.sysScale(), appInfo);
      if (cacheFile == null) {
        return;
      }
      var image = doLoadImage(url);
      if (image != null) {
        saveImage(cacheFile, FileUtilRt.getExtension(url), image);
      }
    });
  }

  private static boolean isCacheNeeded(float scale) {
    return scale != 1 && scale != 2;
  }

  static @Nullable Image doLoadImage(@NotNull String path) {
    return ImageLoader.loadImageForStartUp(path, Splash.class.getClassLoader(), ImageLoader.ALLOW_FLOAT_SCALING, ScaleContext.create(), !path.endsWith(".svg"));
  }

  @Override
  public void paint(Graphics g) {
    if (progress < 0.10 || progressSlidePainter == null) {
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

  public void showProgress(double progress) {
    if (progressColor == null) {
      return;
    }

    if ((progress - this.progress) > 0.01 || progress > 0.99) {
      this.progress = progress;
      Graphics graphics = getGraphics();
      // not yet initialized
      if (graphics != null) {
        paintProgress(graphics);
      }
    }
  }

  private void paintProgress(@Nullable Graphics g) {
    if (g == null) {
      return;
    }

    if (progressSlidePainter != null) {
      progressSlidePainter.paintSlides(g, progress);
    }

    Color progressColor = this.progressColor;
    if (progressColor == null) {
      return;
    }

    int progressWidth = (int)(myWidth * progress);
    int currentWidth = progressWidth - progressLastPosition;
    if (currentWidth == 0) {
      return;
    }

    g.setColor(progressColor);
    int y = progressSlidePainter == null ? progressY : myHeight - progressHeight;
    g.fillRect(progressLastPosition, y, currentWidth, progressHeight);
    if (progressTail != null) {
      int tx = (int)(currentWidth - (progressTail.getIconWidth() / JBUI_INIT_SCALE / 2f * JBUI_INIT_SCALE));
      int ty = (int)(progressY - (progressTail.getIconHeight() - progressHeight) / JBUI_INIT_SCALE / 2f * JBUI_INIT_SCALE);
      progressTail.paintIcon(this, g, tx, ty);
    }
    progressLastPosition = progressWidth;
  }

  private static int uiScale(int i) {
    return (int)(i * JBUI_INIT_SCALE);
  }

   private static void saveImage(@NotNull Path file, String extension, @NotNull Image image) {
     try {
       var tmp = file.resolve(file + ".tmp" + System.currentTimeMillis());
       Files.createDirectories(tmp.getParent());
       try {
         ImageIO.write(ImageUtil.toBufferedImage(image), extension, tmp.toFile());
         try {
           Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE);
         }
         catch (AtomicMoveNotSupportedException e) {
           Files.move(tmp, file);
         }
       }
       finally {
         Files.deleteIfExists(tmp);
       }
     }
     catch (Throwable ignored) {
     }
   }

   private static @Nullable Image loadImageFromCache(@NotNull String path, float scale, @NotNull ApplicationInfoEx appInfo) {
     var file = getCacheFile(path, scale, appInfo);
     if (file == null) {
       return null;
     }

     try {
       if (!Files.isRegularFile(file)) {
         return null;
       }
       Image image = ImageIO.read(file.toFile());
       if (StartupUiUtil.isJreHiDPI()) {
         int w = image.getWidth(ImageLoader.ourComponent);
         int h = image.getHeight(ImageLoader.ourComponent);
         image = new JBHiDPIScaledImage(image, w / (double)scale, h / (double)scale, BufferedImage.TYPE_INT_ARGB);
       }
       return image;
     }
     catch (IOException e) {
       // don't use `error`, because it can crash application
       Logger.getInstance(Splash.class).warn("Failed to load splash image", e);
     }
     return null;
   }

   private static @Nullable Path getCacheFile(@NotNull String path, float scale, @NotNull ApplicationInfoEx appInfo) {
     try {
       var d = MessageDigest.getInstance("SHA-256", Security.getProvider("SUN"));
       d.update(path.getBytes(StandardCharsets.UTF_8));
       // path for EAP and release builds is the same, but content maybe different
       d.update((byte)(appInfo.isEAP() ? 1 : 0));

       String pathToSplash = path.startsWith("/") ? path.substring(1) : path;
       URL resource = Splash.class.getClassLoader().getResource(pathToSplash);
       if (resource != null) {
         long fileSize = Files.size(Paths.get(resource.toURI()));
         ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE).putLong(fileSize);
         d.update(buffer);
       }
       var encodedDigest = Base64.getUrlEncoder().encodeToString(d.digest());
       int dotIndex = path.lastIndexOf('.');
       return Paths.get(PathManager.getSystemPath(), "splashSlides", encodedDigest + '.' + scale + '.' + (dotIndex < 0 ? "" : path.substring(dotIndex)));
     }
     catch (Exception e) {
       return null;
     }
   }
}
