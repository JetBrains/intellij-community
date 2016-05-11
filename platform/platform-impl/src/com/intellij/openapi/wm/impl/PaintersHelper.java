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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ImageLoader;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.VolatileImage;
import java.io.File;
import java.net.URL;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class PaintersHelper implements Painter.Listener {
  private static final Logger LOG = Logger.getInstance(PaintersHelper.class);

  private final Set<Painter> myPainters = ContainerUtil.newLinkedHashSet();
  private final Map<Painter, Component> myPainter2Component = ContainerUtil.newLinkedHashMap();

  private final JComponent myRootComponent;

  public PaintersHelper(@NotNull JComponent component) {
    myRootComponent = component;
  }

  public boolean hasPainters() {
    return !myPainters.isEmpty();
  }

  public boolean needsRepaint() {
    for (Painter painter : myPainters) {
      if (painter.needsRepaint()) return true;
    }
    return false;
  }

  public void addPainter(@NotNull Painter painter, @Nullable Component component) {
    myPainters.add(painter);
    myPainter2Component.put(painter, component == null ? myRootComponent : component);
    painter.addListener(this);
  }

  public void removePainter(@NotNull Painter painter) {
    painter.removeListener(this);
    myPainters.remove(painter);
    myPainter2Component.remove(painter);
  }

  public void clear() {
    for (Painter painter : myPainters) {
      painter.removeListener(this);
    }
    myPainters.clear();
    myPainter2Component.clear();
  }

  public void paint(Graphics g) {
    runAllPainters(g, computeOffsets(g, myRootComponent));
  }

  void runAllPainters(Graphics gg, int[] offsets) {
    if (myPainters.isEmpty()) return;
    Graphics2D g = (Graphics2D)gg;
    AffineTransform orig = g.getTransform();
    int i = 0;
    // restore transform at the time of computeOffset()
    AffineTransform t = new AffineTransform();
    t.translate(offsets[i++], offsets[i++]);

    for (Painter painter : myPainters) {
      if (!painter.needsRepaint()) continue;
      Component cur = myPainter2Component.get(painter);

      g.setTransform(t);
      g.translate(offsets[i++], offsets[i++]);
      painter.paint(cur, g);
    }
    g.setTransform(orig);
  }

  @NotNull
  int[] computeOffsets(Graphics gg, @NotNull JComponent component) {
    if (myPainters.isEmpty()) return ArrayUtil.EMPTY_INT_ARRAY;
    int i = 0;
    int[] offsets = new int[2 + myPainters.size() * 2];
    // store current graphics transform
    Graphics2D g = (Graphics2D)gg;
    AffineTransform transform = g.getTransform();
    offsets[i++] = (int)transform.getTranslateX();
    offsets[i++] = (int)transform.getTranslateY();
    // calculate relative offsets for painters
    Rectangle r = null;
    Component prev = null;
    for (Painter painter : myPainters) {
      if (!painter.needsRepaint()) continue;

      Component cur = myPainter2Component.get(painter);
      if (cur != prev || r == null) {
        Container curParent = cur.getParent();
        if (curParent == null) continue;
        r = SwingUtilities.convertRectangle(curParent, cur.getBounds(), component);
        prev = cur;
      }
      offsets[i++] = r.x;
      offsets[i++] = r.y;
    }
    return offsets;
  }

  @Override
  public void onNeedsRepaint(Painter painter, JComponent dirtyComponent) {
    if (dirtyComponent != null && dirtyComponent.isShowing()) {
      Rectangle rec = SwingUtilities.convertRectangle(dirtyComponent, dirtyComponent.getBounds(), myRootComponent);
      myRootComponent.repaint(rec);
    }
    else {
      myRootComponent.repaint();
    }
  }

  public enum FillType {
    BG_CENTER, TILE, SCALE,
    CENTER, TOP_CENTER, BOTTOM_CENTER,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
  }

  public static void initWallpaperPainter(@NotNull String propertyName, @NotNull PaintersHelper painters) {
    ImagePainter painter = (ImagePainter)newWallpaperPainter(propertyName);
    painters.addPainter(painter, null);
  }

  private static AbstractPainter newWallpaperPainter(@NotNull final String propertyName) {
    return new ImagePainter() {
      Image image;
      float alpha;
      Insets insets;
      FillType fillType;

      String current;

      @Override
      public boolean needsRepaint() {
        return ensureImageLoaded();
      }

      @Override
      public void executePaint(Component component, Graphics2D g) {
        if (image == null) return; // covered by needsRepaint()
        executePaint(g, component, image, fillType, alpha, insets);
      }

      boolean ensureImageLoaded() {
        String value = System.getProperty(propertyName);
        if (!Comparing.equal(value, current)) {
          current = value;
          loadImageAsync(value);
          // keep the current image for a while
        }
        return image != null;
      }

      private void resetImage(String value, Image newImage, float newAlpha, FillType newFillType) {
        if (!Comparing.equal(current, value)) return;
        boolean prevOk = image != null;
        clearImages(-1);
        image = newImage;
        insets = JBUI.emptyInsets();
        alpha = newAlpha;
        fillType = newFillType;
        boolean newOk = newImage != null;
        if (prevOk || newOk) {
          repaintAllWindows();
        }
      }

      private void loadImageAsync(final String propertyValue) {
        String[] parts = (propertyValue != null ? propertyValue : propertyName + ".png").split(",");
        final float newAlpha = Math.abs(Math.min(StringUtil.parseInt(parts.length > 1 ? parts[1] : "", 10) / 100f, 1f));
        final FillType newFillType = StringUtil.parseEnum(parts.length > 2 ? parts[2].toUpperCase(Locale.ENGLISH) : "", FillType.SCALE, FillType.class);
        try {
          String filePath = parts[0];
          URL url = filePath.contains("://") ? new URL(filePath) :
                    (FileUtil.isAbsolutePlatformIndependent(filePath)
                     ? new File(filePath)
                     : new File(PathManager.getConfigPath(), filePath)).toURI().toURL();
          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
              final Image m = ImageLoader.loadFromUrl(url);
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  resetImage(propertyValue, m, newAlpha, newFillType);
                }
              });
            }
          });
        }
        catch (Exception e) {
          resetImage(propertyValue, null, newAlpha, newFillType);
        }
      }
    };
  }

  private static void repaintAllWindows() {
    for (Window window : Window.getWindows()) {
      window.repaint();
    }
  }

  public static AbstractPainter newImagePainter(@NotNull final Image image, final FillType fillType, final float alpha, final Insets insets) {
    return new ImagePainter() {
      @Override
      public boolean needsRepaint() {
        return true;
      }

      @Override
      public void executePaint(Component component, Graphics2D g) {
        executePaint(g, component, image, fillType, alpha, insets);
      }
    };
  }

  private static class Cached {
    final VolatileImage image;
    final Dimension used;
    long touched;

    Cached(VolatileImage image, Dimension dim) {
      this.image = image;
      used = dim;
    }
  }
  
  private abstract static class ImagePainter extends AbstractPainter {

    final Map<GraphicsConfiguration, Cached> cachedMap = ContainerUtil.newHashMap();

    public void executePaint(Graphics2D g, Component component, Image image, FillType fillType, float alpha, Insets insets) {
      int cw0 = component.getWidth();
      int ch0 = component.getHeight();
      Insets i = JBUI.insets(insets.top * ch0 / 100, insets.left * cw0 / 100, insets.bottom * ch0 / 100, insets.right * cw0 / 100);
      int cw = cw0 - i.left - i.right;
      int ch = ch0 - i.top - i.bottom;
      int w = image.getWidth(null);
      int h = image.getHeight(null);
      if (w <= 0 || h <= 0) return;
      // performance: pre-compute scaled image or tiles
      @Nullable
      GraphicsConfiguration cfg = g.getDeviceConfiguration();
      Cached cached = cachedMap.get(cfg);
      VolatileImage scaled = cached == null ? null : cached.image;
      if (fillType == FillType.SCALE || fillType == FillType.TILE) {
        int sw, sh;
        if (fillType == FillType.SCALE) {
          boolean useWidth = cw * h > ch * w;
          sw = useWidth ? cw : w * ch / h;
          sh = useWidth ? h * cw / w : ch;
        }
        else {
          sw = cw < w ? w : (cw + w) / w * w;
          sh = ch < h ? h : (ch + h) / h * h;
        }
        int sw0 = scaled == null ? -1 : scaled.getWidth(null);
        int sh0 = scaled == null ? -1 : scaled.getHeight(null);
        boolean rescale = cached == null || cached.used.width != sw || cached.used.height != sh;
        while ((scaled = validateImage(cfg, scaled)) == null || rescale) {
          if (scaled == null || sw0 < sw || sh0 < sh) {
            scaled = createImage(cfg, sw + sw / 10, sh + sh / 10); // + 10 percent
            cachedMap.put(cfg, cached = new Cached(scaled, new Dimension(sw, sh)));
          }
          else {
            cached.used.setSize(sw, sh);
          }
          Graphics2D gg = scaled.createGraphics();
          gg.setComposite(AlphaComposite.Src);
          if (fillType == FillType.SCALE) {
            gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gg.drawImage(image, 0, 0, sw, sh, null);
          }
          else {
            for (int x = 0; x < sw; x += w) {
              for (int y = 0; y < sh; y += h) {
                UIUtil.drawImage(gg, image, x, y, null);
              }
            }
          }
          gg.dispose();
          rescale = false;
        }
        w = sw;
        h = sh;
      }
      else {
        while ((scaled = validateImage(cfg, scaled)) == null) {
          scaled = createImage(cfg, w, h);
          cachedMap.put(cfg, cached = new Cached(scaled, new Dimension(w, h)));
          Graphics2D gg = scaled.createGraphics();
          gg.setComposite(AlphaComposite.Src);
          gg.drawImage(image, 0, 0, null);
          gg.dispose();
        }
      }
      long currentTime = System.currentTimeMillis();
      cached.touched = currentTime;
      if (cachedMap.size() > 2) {
        clearImages(currentTime);
      }

      int x, y;
      if (fillType == FillType.CENTER || fillType == FillType.BG_CENTER ||
          fillType == FillType.SCALE || fillType == FillType.TILE ||
          fillType == FillType.TOP_CENTER || fillType == FillType.BOTTOM_CENTER) {
        x = i.left + (cw - w) / 2;
        y = fillType == FillType.TOP_CENTER ? i.top :
            fillType == FillType.BOTTOM_CENTER ? ch0 - i.bottom - h :
            i.top + (ch - h) / 2;
      }
      else if (fillType == FillType.TOP_LEFT || fillType == FillType.TOP_RIGHT ||
               fillType == FillType.BOTTOM_LEFT || fillType == FillType.BOTTOM_RIGHT) {
        x = fillType == FillType.TOP_LEFT || fillType == FillType.BOTTOM_LEFT ? i.left : cw0 - i.right - w;
        y = fillType == FillType.TOP_LEFT || fillType == FillType.TOP_RIGHT ? i.top : ch0 - i.bottom - h;
      }
      else {
        return;
      }

      GraphicsConfig gc = new GraphicsConfig(g).setAlpha(alpha);
      UIUtil.drawImage(g, scaled, x, y, w, h, null);
      if (fillType == FillType.BG_CENTER) {
        g.setColor(component.getBackground());
        g.fillRect(0, 0, x, ch0);
        g.fillRect(x, 0, w, h);
        g.fillRect(x + w, 0, x, ch0);
        g.fillRect(x, y + h, w, y);
      }

      gc.restore();
    }

    void clearImages(long currentTime) {
      boolean all = currentTime <= 0;
      for (Iterator<GraphicsConfiguration> it = cachedMap.keySet().iterator(); it.hasNext(); ) {
        GraphicsConfiguration cfg = it.next();
        Cached c = cachedMap.get(cfg);
        if (all || currentTime - c.touched > 2 * 60 * 1000L) {
          it.remove();
          LOG.info(logPrefix(cfg, c.image) + "image flushed" +
                   (all ? "" : "; untouched for " + StringUtil.formatDuration(currentTime - c.touched)));
          c.image.flush();
        }
      }
    }

    @Nullable
    private static VolatileImage validateImage(@Nullable GraphicsConfiguration cfg, @Nullable VolatileImage image) {
      if (image == null) return null;
      boolean lost1 = image.contentsLost();
      int validated = image.validate(cfg);
      boolean lost2 = image.contentsLost();
      if (lost1 || lost2 || validated != VolatileImage.IMAGE_OK) {
        LOG.info(logPrefix(cfg, image) + "image flushed" +
                 ": contentsLost=" + lost1 + "||" + lost2 + "; validate=" + validated);
        image.flush();
        return null;
      }
      return image;
    }

    @NotNull
    private static VolatileImage createImage(@Nullable GraphicsConfiguration cfg, int w, int h) {
      GraphicsConfiguration safe;
      safe = cfg != null ? cfg : GraphicsEnvironment.getLocalGraphicsEnvironment()
        .getDefaultScreenDevice().getDefaultConfiguration();
      VolatileImage image;
      try {
        image = safe.createCompatibleVolatileImage(w, h, new ImageCapabilities(true), Transparency.TRANSLUCENT);
      }
      catch (Exception e) {
        image = safe.createCompatibleVolatileImage(w, h, Transparency.TRANSLUCENT);
      }
      // validate first time (it's always RESTORED & cleared)
      image.validate(cfg);
      image.setAccelerationPriority(1f);
      ImageCapabilities caps = image.getCapabilities();
      LOG.info(logPrefix(cfg, image) +
               (caps.isAccelerated() ? "" : "non-") + "accelerated " +
               (caps.isTrueVolatile() ? "" : "non-") + "volatile " +
               "image created");
      return image;
    }

    @NotNull
    private static String logPrefix(@Nullable GraphicsConfiguration cfg, @NotNull VolatileImage image) {
      return "(" + (cfg == null ? "null" : cfg.getClass().getSimpleName()) + ") "
             + image.getWidth() + "x" + image.getHeight() + " ";
    }
  }
}
