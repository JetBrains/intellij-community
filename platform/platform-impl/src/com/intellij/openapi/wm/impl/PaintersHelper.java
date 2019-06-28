// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.*;
import java.io.File;
import java.net.URL;
import java.util.*;

import static com.intellij.openapi.wm.impl.IdeBackgroundUtil.Anchor.*;
import static com.intellij.openapi.wm.impl.IdeBackgroundUtil.Fill.SCALE;
import static com.intellij.openapi.wm.impl.IdeBackgroundUtil.Fill.TILE;
import static com.intellij.openapi.wm.impl.IdeBackgroundUtil.getBackgroundSpec;

final class PaintersHelper implements Painter.Listener {
  private static final Logger LOG = Logger.getInstance(PaintersHelper.class);

  private final Set<Painter> myPainters = new LinkedHashSet<>();
  private final Map<Painter, Component> myPainter2Component = new LinkedHashMap<>();

  private final JComponent myRootComponent;

  PaintersHelper(@NotNull JComponent component) {
    myRootComponent = component;
  }

  boolean hasPainters() {
    return !myPainters.isEmpty();
  }

  public boolean needsRepaint() {
    for (Painter painter : myPainters) {
      if (painter.needsRepaint()) return true;
    }
    return false;
  }

  void addPainter(@NotNull Painter painter, @Nullable Component component) {
    myPainters.add(painter);
    myPainter2Component.put(painter, component == null ? myRootComponent : component);
    painter.addListener(this);
  }

  void removePainter(@NotNull Painter painter) {
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

  void runAllPainters(Graphics gg, @Nullable Offsets offsets) {
    if (myPainters.isEmpty() || offsets == null) return;
    Graphics2D g = (Graphics2D)gg;
    AffineTransform orig = g.getTransform();
    int i = 0;
    for (Painter painter : myPainters) {
      if (!painter.needsRepaint()) continue;
      Component cur = myPainter2Component.get(painter);
      // restore transform at the time of computeOffset()
      g.setTransform(offsets.transform);
      g.translate(offsets.offsets[i++], offsets.offsets[i++]);
      painter.paint(cur, g);
    }
    g.setTransform(orig);
  }

  @Nullable
  Offsets computeOffsets(Graphics gg, @NotNull JComponent component) {
    if (myPainters.isEmpty()) return null;
    Offsets offsets = new Offsets();
    offsets.offsets = new int[myPainters.size() * 2];
    // store current graphics transform
    Graphics2D g = (Graphics2D)gg;
    offsets.transform = new AffineTransform(g.getTransform());
    // calculate relative offsets for painters
    Rectangle r = null;
    Component prev = null;
    int i = 0;
    for (Painter painter : myPainters) {
      if (!painter.needsRepaint()) continue;

      Component cur = myPainter2Component.get(painter);
      if (cur != prev || r == null) {
        Container curParent = cur.getParent();
        if (curParent == null) continue;
        r = SwingUtilities.convertRectangle(curParent, cur.getBounds(), component);
        prev = cur;
      }
      // component offsets don't include graphics scale, so compensate
      offsets.offsets[i++] = r.x;
      offsets.offsets[i++] = r.y;
    }
    return offsets;
  }

  public static class Offsets {
    AffineTransform transform;
    int[] offsets;
  }

  @Override
  public void onNeedsRepaint(@NotNull Painter painter, JComponent dirtyComponent) {
    if (dirtyComponent != null && dirtyComponent.isShowing()) {
      Rectangle rec = SwingUtilities.convertRectangle(dirtyComponent, dirtyComponent.getBounds(), myRootComponent);
      myRootComponent.repaint(rec);
    }
    else {
      myRootComponent.repaint();
    }
  }

  static void initWallpaperPainter(@NotNull String propertyName, @NotNull PaintersHelper painters) {
    ImagePainter painter = (ImagePainter)newWallpaperPainter(propertyName, painters.myRootComponent);
    painters.addPainter(painter, null);
  }

  private static AbstractPainter newWallpaperPainter(@NotNull String propertyName,
                                                     @NotNull JComponent rootComponent) {
    return new ImagePainter() {
      Image image;
      float alpha;
      Insets insets;
      IdeBackgroundUtil.Fill fillType;
      IdeBackgroundUtil.Anchor anchor;

      String current;

      @Override
      public boolean needsRepaint() {
        return ensureImageLoaded();
      }

      @Override
      public void executePaint(Component component, Graphics2D g) {
        if (image == null) return; // covered by needsRepaint()
        executePaint(g, component, image, fillType, anchor, alpha, insets);
      }

      boolean ensureImageLoaded() {
        IdeFrame frame = ComponentUtil.getParentOfType((Class<? extends IdeFrame>)IdeFrame.class, (Component)rootComponent);
        Project project = frame == null ? null : frame.getProject();
        String value = getBackgroundSpec(project, propertyName);
        if (!Comparing.equal(value, current)) {
          current = value;
          loadImageAsync(value);
          // keep the current image for a while
        }
        return image != null;
      }

      private void resetImage(String value, Image newImage, float newAlpha, IdeBackgroundUtil.Fill newFill, IdeBackgroundUtil.Anchor newAnchor) {
        if (!Comparing.equal(current, value)) return;
        boolean prevOk = image != null;
        clearImages(-1);
        image = newImage;
        insets = JBUI.emptyInsets();
        alpha = newAlpha;
        fillType = newFill;
        anchor = newAnchor;
        boolean newOk = newImage != null;
        if (prevOk || newOk) {
          ModalityState modalityState = ModalityState.stateForComponent(rootComponent);
          if (modalityState.dominates(ModalityState.NON_MODAL)) {
            UIUtil.getActiveWindow().repaint();
          }
          else {
            IdeBackgroundUtil.repaintAllWindows();
          }
        }
      }

      private void loadImageAsync(@Nullable String propertyValue) {
        String[] parts = (propertyValue != null ? propertyValue : propertyName + ".png").split(",");
        float newAlpha = Math.abs(Math.min(StringUtil.parseInt(parts.length > 1 ? parts[1] : "", 10) / 100f, 1f));
        IdeBackgroundUtil.Fill newFillType = StringUtil.parseEnum(parts.length > 2 ? StringUtil.toUpperCase(parts[2]) : "", SCALE, IdeBackgroundUtil.Fill.class);
        IdeBackgroundUtil.Anchor newAnchor = StringUtil.parseEnum(parts.length > 3 ? StringUtil.toUpperCase(parts[3]) : "", CENTER, IdeBackgroundUtil.Anchor.class);
        String flip = parts.length > 4 ? parts[4] : "none";
        String filePath = parts[0];
        if (StringUtil.isEmpty(filePath)) {
          resetImage(propertyValue, null, newAlpha, newFillType, newAnchor);
          return;
        }
        try {
          URL url = filePath.contains("://") ? new URL(filePath) :
                    (FileUtil.isAbsolutePlatformIndependent(filePath)
                     ? new File(filePath)
                     : new File(PathManager.getConfigPath(), filePath)).toURI().toURL();
          ModalityState modalityState = ModalityState.stateForComponent(rootComponent);
          boolean flipH = "flipHV".equals(flip) || "flipH".equals(flip);
          boolean flipV = "flipHV".equals(flip) || "flipV".equals(flip);
          ApplicationManager.getApplication().executeOnPooledThread(() -> {
            BufferedImageFilter flipFilter = flipV || flipH ? flipFilter(flipV, flipH) : null;
            Image m = ImageLoader.loadFromUrl(url, true, true, new ImageFilter[]{flipFilter}, ScaleContext.create());
            ApplicationManager.getApplication().invokeLater(() -> resetImage(propertyValue, m, newAlpha, newFillType, newAnchor), modalityState);
          });
        }
        catch (Exception e) {
          resetImage(propertyValue, null, newAlpha, newFillType, newAnchor);
        }
      }
    };
  }

  static AbstractPainter newImagePainter(@NotNull Image image,
                                         @NotNull IdeBackgroundUtil.Fill fillType,
                                         @NotNull IdeBackgroundUtil.Anchor anchor,
                                         float alpha,
                                         @NotNull Insets insets) {
    return new ImagePainter() {
      @Override
      public boolean needsRepaint() {
        return true;
      }

      @Override
      public void executePaint(Component component, Graphics2D g) {
        executePaint(g, component, image, fillType, anchor, alpha, insets);
      }
    };
  }

  private static class Cached {
    final VolatileImage image;
    final Rectangle src;
    final Rectangle dst;
    long touched;

    Cached(VolatileImage image, Rectangle src, Rectangle dst) {
      this.image = image;
      this.src = src;
      this.dst = dst;
    }
  }

  private abstract static class ImagePainter extends AbstractPainter {

    final Map<GraphicsConfiguration, Cached> cachedMap = new HashMap<>();

    void executePaint(@NotNull Graphics2D g,
                      @NotNull Component component,
                      @NotNull Image image,
                      @NotNull IdeBackgroundUtil.Fill fillType,
                      @NotNull IdeBackgroundUtil.Anchor anchor,
                      float alpha,
                      @NotNull Insets insets) {
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
      Rectangle src0 = new Rectangle();
      Rectangle dst0 = new Rectangle();
      calcSrcDst(src0, dst0, w, h, cw, ch, fillType);
      alignRect(src0, w, h, anchor);
      if (fillType == TILE) {
        alignRect(dst0, cw, ch, anchor);
      }
      int sw0 = scaled == null ? -1 : scaled.getWidth(null);
      int sh0 = scaled == null ? -1 : scaled.getHeight(null);
      boolean repaint = cached == null || !cached.src.equals(src0) || !cached.dst.equals(dst0);
      while ((scaled = validateImage(cfg, scaled)) == null || repaint) {
        int sw = Math.min(cw, dst0.width);
        int sh = Math.min(ch, dst0.height);
        if (scaled == null || sw0 < sw || sh0 < sh) {
          scaled = createImage(cfg, sw, sh);
          cachedMap.put(cfg, cached = new Cached(scaled, src0, dst0));
        }
        else {
          cached.src.setBounds(src0);
          cached.dst.setBounds(dst0);
        }
        Graphics2D gg = scaled.createGraphics();
        gg.setComposite(AlphaComposite.Src);
        if (fillType == SCALE) {
          gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                              RenderingHints.VALUE_INTERPOLATION_BILINEAR);
          UIUtil.drawImage(gg, image, dst0, src0, null);
        }
        else if (fillType == TILE) {
          Rectangle r = new Rectangle(0, 0, 0, 0);
          for (int x = 0; x < dst0.width; x += w) {
            for (int y = 0; y < dst0.height; y += h) {
              r.setBounds(dst0.x + x, dst0.y + y, src0.width, src0.height);
              UIUtil.drawImage(gg, image, r, src0, null);
            }
          }
        }
        else {
          UIUtil.drawImage(gg, image, dst0, src0, null);
        }
        gg.dispose();
        repaint = false;
      }
      long currentTime = System.currentTimeMillis();
      cached.touched = currentTime;
      if (cachedMap.size() > 2) {
        clearImages(currentTime);
      }
      Rectangle src = new Rectangle(0, 0, cw, ch);
      Rectangle dst = new Rectangle(i.left, i.top, cw, ch);
      if (fillType != TILE) {
        alignRect(src, dst0.width, dst0.height, anchor);
      }

      float adjustedAlpha = Boolean.TRUE.equals(g.getRenderingHint(IdeBackgroundUtil.ADJUST_ALPHA)) ? 0.65f * alpha : alpha;
      GraphicsConfig gc = new GraphicsConfig(g).setAlpha(adjustedAlpha);
      UIUtil.drawImage(g, scaled, dst, src, null, null);
      gc.restore();
    }

    static void calcSrcDst(Rectangle src,
                           Rectangle dst,
                           int w,
                           int h,
                           int cw,
                           int ch,
                           IdeBackgroundUtil.Fill fillType) {
      if (fillType == SCALE) {
        boolean useWidth = cw * h > ch * w;
        int sw = useWidth ? w : cw * h / ch;
        int sh = useWidth ? ch * w / cw : h;

        src.setBounds(0, 0, sw, sh);
        dst.setBounds(0, 0, cw, ch);
      }
      else if (fillType == TILE) {
        int dw = cw < w ? w : ((cw / w + 1) / 2 * 2 + 1) * w;
        int dh = ch < h ? h : ((ch / h + 1) / 2 * 2 + 1) * h;
        // tile rectangles are not clipped for proper anchor support
        src.setBounds(0, 0, w, h);
        dst.setBounds(0, 0, dw, dh);
      }
      else {
        src.setBounds(0, 0, Math.min(w, cw), Math.min(h, ch));
        dst.setBounds(src);
      }
    }

    static void alignRect(Rectangle r, int w, int h, IdeBackgroundUtil.Anchor anchor) {
      if (anchor == TOP_CENTER ||
          anchor == CENTER ||
          anchor == BOTTOM_CENTER) {
        r.x = (w - r.width) / 2;
        r.y = anchor == TOP_CENTER ? 0 :
              anchor == BOTTOM_CENTER ? h - r.height :
              (h - r.height) / 2;
      }
      else {
        r.x = anchor == TOP_LEFT ||
              anchor == MIDDLE_LEFT ||
              anchor == BOTTOM_LEFT ? 0 : w - r.width;
        r.y = anchor == TOP_LEFT || anchor == TOP_RIGHT ? 0 :
              anchor == BOTTOM_LEFT || anchor == BOTTOM_RIGHT ? h - r.height :
              (h - r.height) / 2;
      }
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
      GraphicsConfiguration safe = cfg != null ? cfg : GraphicsEnvironment.getLocalGraphicsEnvironment()
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

    @NotNull
    static BufferedImageFilter flipFilter(boolean flipV, boolean flipH) {
      return new BufferedImageFilter(new BufferedImageOp() {
        @Override
        public BufferedImage filter(BufferedImage src, BufferedImage dest) {
          AffineTransform tx = AffineTransform.getScaleInstance(flipH ? -1 : 1, flipV ? -1 : 1);
          tx.translate(flipH ? -src.getWidth(null) : 0, flipV ? -src.getHeight(null) : 0);
          AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
          return op.filter(src, dest);
        }

        @Override
        public Rectangle2D getBounds2D(BufferedImage src) { return null;}

        @Override
        public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM) { return null;}

        @Override
        public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) { return null;}

        @Override
        public RenderingHints getRenderingHints() { return null;}
      });
    }
  }
}
