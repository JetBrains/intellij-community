// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.RetinaImage;
import com.jetbrains.JBR;
import com.jetbrains.cef.SharedMemory;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefNativeRenderHandler;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.VolatileImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
class JBCefNativeOsrHandler extends JBCefOsrHandler implements CefNativeRenderHandler {
  private static final int CLEAN_CACHE_TIME_MS = Integer.getInteger("jcef.remote.osr.clean_cache_time_ms", 10*1000); // 10 sec
  private static final boolean FORCE_USE_SOFTWARE_RENDERING = Boolean.getBoolean("jcef.remote.force_use_software_rendering");

  private final Map<String, SharedMemory.WithRaster> mySharedMemCache = new ConcurrentHashMap<>();
  private SharedMemory.WithRaster myCurrentFrame;
  private volatile boolean myIsDisposed = false;

  @Override
  synchronized public void disposeNativeResources() {
    if (myIsDisposed)
      return;

    myIsDisposed = true;
    mySharedMemCache.clear();
  }

  private void cleanCacheIfNecessary() {
    final long timeMs = System.currentTimeMillis();
    if (mySharedMemCache.size() < 2)
      return;

    ArrayList<String> toRemove = new ArrayList<>();
    for (Map.Entry<String, SharedMemory.WithRaster> item: mySharedMemCache.entrySet()) {
      if (timeMs - item.getValue().lasUsedMs > CLEAN_CACHE_TIME_MS) {
        toRemove.add(item.getKey());
      }
    }
    for (String name: toRemove) {
      mySharedMemCache.remove(name);
    }
  }

  @Override
  public void onPaintWithSharedMem(CefBrowser browser,
                                   boolean popup,
                                   int dirtyRectsCount,
                                   String sharedMemName,
                                   long sharedMemHandle,
                                   int width,
                                   int height) {
    SharedMemory.WithRaster mem = mySharedMemCache.get(sharedMemName);
    if (mem == null) {
      cleanCacheIfNecessary();
      mem = new SharedMemory.WithRaster(sharedMemName, sharedMemHandle);
      synchronized (this) {
        // Use synchronization to avoid leak (when disposeNativeRes is called just before putting into cache).
        if (myIsDisposed)
          return;
        mySharedMemCache.put(sharedMemName, mem);
      }
    }

    mem.setWidth(width);
    mem.setHeight(height);
    mem.setDirtyRectsCount(dirtyRectsCount);
    mem.lasUsedMs = System.currentTimeMillis();

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
    return frame == null ? null : new Dimension(frame.getWidth(), frame.getHeight());
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
        if (!FORCE_USE_SOFTWARE_RENDERING && JBR.isNativeRasterLoaderSupported()) {
          JBR.getNativeRasterLoader().loadNativeRaster(vi, frame.getPtr(), frame.getWidth(), frame.getHeight(),
                                                       frame.getPtr() + 4L * frame.getWidth() * frame.getHeight(),
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
    final int width = mem.getWidth();
    final int height = mem.getHeight();
    final int rectsCount = mem.getDirtyRectsCount();

    ByteBuffer buffer = mem.wrapRaster();
    int[] dst = ((DataBufferInt)bufImage.getRaster().getDataBuffer()).getData();
    IntBuffer src = buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();

    Rectangle[] dirtyRects = new Rectangle[]{new Rectangle(0, 0, width, height)};
    if (rectsCount > 0) {
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

    // flip image here
    // TODO: consider to optimize
    for (Rectangle rect : dirtyRects) {
      if (rect.width < width) {
        for (int line = rect.y; line < rect.y + rect.height; line++) {
          int offset = line*width + rect.x;
          src.position(offset).get(dst, offset, rect.width);
        }
      }
      else { // optimized for a buffer wide dirty rect
        src.position(rect.y*width).get(dst, rect.y*width, width*rect.height);
      }
    }

    // draw debug
    //            Graphics2D g = bufImage.createGraphics();
    //            g.setColor(Color.RED);
    //            for (Rectangle r : dirtyRects)
    //                g.drawRect(r.x, r.y, r.width, r.height);
    //            g.dispose();
  }
}
