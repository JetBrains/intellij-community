// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.Function;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.RetinaImage;
import com.jetbrains.JBR;
import com.jetbrains.cef.SharedMemory;
import com.jetbrains.cef.SharedMemoryCache;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefNativeRenderHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.VolatileImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Objects;

@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
class JBCefNativeOsrHandler extends JBCefOsrHandler implements CefNativeRenderHandler {
  private static final boolean FORCE_USE_SOFTWARE_RENDERING;

  static {
    if (SystemInfoRt.isMac || SystemInfoRt.isLinux)
      FORCE_USE_SOFTWARE_RENDERING = Boolean.getBoolean("jcef.remote.use_software_rendering");
    else
      FORCE_USE_SOFTWARE_RENDERING = !Boolean.getBoolean("jcef.remote.enable_hardware_rendering"); // NOTE: temporary enabled until fixed IJPL-161293
  }

  private final SharedMemoryCache mySharedMemCache = new SharedMemoryCache();
  private SharedMemory.WithRaster myCurrentFrame;

  JBCefNativeOsrHandler(@NotNull JComponent component, @NotNull Function<? super JComponent, ? extends Rectangle> screenBoundsProvider) {
    super(component, screenBoundsProvider);
  }

  @Override
  public synchronized void disposeNativeResources() {}

  @Override
  public void onPaintWithSharedMem(CefBrowser browser,
                                   boolean popup,
                                   int dirtyRectsCount,
                                   String sharedMemName,
                                   long sharedMemHandle,
                                   int width,
                                   int height) {
    SharedMemory.WithRaster mem = mySharedMemCache.get(sharedMemName, sharedMemHandle);
    mem.setWidth(width);
    mem.setHeight(height);
    mem.setDirtyRectsCount(dirtyRectsCount);

    if (popup) {
      JBHiDPIScaledImage image = myPopupImage;
      if (image == null || image.getDelegate() == null
          || image.getDelegate().getWidth(null) != width
          || image.getDelegate().getHeight(null) != height) {
        image = (JBHiDPIScaledImage)RetinaImage.createFrom(
          new BufferedImage(mem.getWidth(), mem.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE),
          getPixelDensity(), null);
      }
      synchronized (myPopupMutex) {
        loadBuffered((BufferedImage)Objects.requireNonNull(image.getDelegate()), mem);
        myPopupImage = image;
      }
    } else {
      myCurrentFrame = mem;
    }

    // TODO: calculate outerRect
    myContentOutdated = true;
    SwingUtilities.invokeLater(() -> {
      if (!browser.getUIComponent().isShowing()) return;
      JRootPane root = SwingUtilities.getRootPane(browser.getUIComponent());
      RepaintManager rm = RepaintManager.currentManager(root);
      Rectangle dirtySrc = new Rectangle(0, 0, browser.getUIComponent().getWidth(), browser.getUIComponent().getHeight());
      Rectangle dirtyDst = SwingUtilities.convertRectangle(browser.getUIComponent(), dirtySrc, root);
      int dx = 1;
      // NOTE: should mark area outside browser (otherwise background component won't be repainted)
      rm.addDirtyRegion(root, dirtyDst.x - dx, dirtyDst.y - dx, dirtyDst.width + dx * 2, dirtyDst.height + dx * 2);
    });
  }

  @Override
  protected Dimension getCurrentFrameSize() {
    SharedMemory.WithRaster frame = myCurrentFrame;
    if (frame == null)
      return null;

    return new Dimension((int)Math.ceil(frame.getWidth()/getPixelDensity()), (int)Math.ceil(frame.getHeight()/getPixelDensity()));
  }

  @Override
  protected void drawVolatileImage(VolatileImage vi) {
    final SharedMemory.WithRaster frame = myCurrentFrame;
    if (frame == null) // nothing to do.
      return;

    // Shared-memory frame presented, so draw it into volatile image.
    synchronized (frame) {
      try {
        frame.lock();
        if (useNativeRasterLoader()) {
          JBR.getNativeRasterLoader().loadNativeRaster(vi, frame.getPtr(), frame.getWidth(), frame.getHeight(),
                                                       frame.getPtr() + frame.getRectsOffset(),
                                                       frame.getDirtyRectsCount());
          return;
        }

        // Use slow code-path: load shared memory into buffered image
        JBHiDPIScaledImage image = myImage;
        if (image == null || image.getDelegate() == null
            || image.getDelegate().getWidth(null) != frame.getWidth()
            || image.getDelegate().getHeight(null) != frame.getHeight()) {
          image = (JBHiDPIScaledImage)RetinaImage.createFrom(
            new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE),
            getPixelDensity(), null);
        }
        loadBuffered((BufferedImage)Objects.requireNonNull(image.getDelegate()), frame);
        myImage = image;
      }
      finally {
        frame.unlock();
      }
    }

    // We are here then !JBR.isNativeRasterLoaderSupported() => myImage is prepared to be drawn onto volatile image.
    super.drawVolatileImage(vi);
  }

  private static void loadBuffered(BufferedImage bufImage, SharedMemory.WithRaster mem) {
    final int srcW = mem.getWidth();
    final int srcH = mem.getHeight();
    ByteBuffer srcBuffer = mem.wrapRaster();
    IntBuffer src = srcBuffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();

    final int dstW = bufImage.getRaster().getWidth();
    final int dstH = bufImage.getRaster().getHeight();
    int[] dst = ((DataBufferInt)bufImage.getRaster().getDataBuffer()).getData();

    final int rectsCount = mem.getDirtyRectsCount();
    Rectangle[] dirtyRects = new Rectangle[]{new Rectangle(0, 0, srcW, srcH)};
    if (rectsCount > 0) {
      dirtyRects = new Rectangle[rectsCount];
      ByteBuffer rectsMem = mem.wrapRects();
      IntBuffer rects = rectsMem.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
      for (int c = 0; c < rectsCount; ++c) {
        int pos = c*4;
        Rectangle r = new Rectangle();
        r.x = rects.get(pos++);
        r.y = rects.get(pos++);
        r.width = rects.get(pos++);
        r.height = rects.get(pos);
        dirtyRects[c] = r;
      }
    }

    for (Rectangle rect : dirtyRects) {
      if (rect.width < srcW || dstW != srcW) {
        for (int line = rect.y; line < rect.y + rect.height; line++)
          copyLine(src, srcW, srcH, dst, dstW, dstH, rect.x, line, rect.x + rect.width);
      } else {
        // Optimization for a buffer wide dirty rect
        // rect.width == srcW && dstW == srcW
        int offset = rect.y*srcW;
        if (rect.y + rect.height <= dstH)
          src.position(offset).get(dst, offset, srcW*rect.height);
        else
          src.position(offset).get(dst, offset, srcW*(dstH - rect.y));
      }
    }

    // draw debug
    //            Graphics2D g = bufImage.createGraphics();
    //            g.setColor(Color.RED);
    //            for (Rectangle r : dirtyRects)
    //                g.drawRect(r.x, r.y, r.width, r.height);
    //            g.dispose();
  }

  private static void copyLine(IntBuffer src, int sw, int sh, int[] dst, int dw, int dh, int x0, int y0, int x1) {
    if (x0 < 0 || x0 >= sw || x0 >= dw || x1 <= x0)
      return;
    if (y0 < 0 || y0 >= sh || y0 >= dh)
      return;

    int offsetSrc = y0*sw + x0;
    int offsetDst = y0*dw + x0;
    if (x1 > dw)
      src.position(offsetSrc).get(dst, offsetDst, dw - x0);
    else
      src.position(offsetSrc).get(dst, offsetDst, x1 - x0);
  }

  private static Boolean useNativeRasterLoader() {
    return !FORCE_USE_SOFTWARE_RENDERING && JBR.isNativeRasterLoaderSupported();
  }

  @Override
  Color getColorAt(int x, int y) {
    if (!useNativeRasterLoader()) {
      return super.getColorAt(x, y);
    }

    if (myCurrentFrame == null) {
      return null;
    }
    try {
      myCurrentFrame.lock();

      ByteBuffer byteBuffer = myCurrentFrame.wrapRaster();
      if (x < 0 || x >= myCurrentFrame.getWidth() || y < 0 || y >= myCurrentFrame.getHeight()) {
        return null;
      }
      Color color = new Color(byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(y * myCurrentFrame.getWidth() + x), true);
      return color;
    } finally {
      myCurrentFrame.unlock();
    }
  }
}
