// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.lang.UrlClassLoader;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NST {
  private static final Logger LOG = Logger.getInstance(NST.class);
  private static final String ourRegistryKeyTouchbar = "ide.mac.touchbar.use";
  private static NSTLibrary ourNSTLibrary = null; // NOTE: JNA is stateless (doesn't have any limitations of multi-threaded use)

  private static String MIN_OS_VERSION = "10.12.2";
  static boolean isSupportedOS() { return SystemInfo.isMac && SystemInfo.isOsVersionAtLeast(MIN_OS_VERSION); }

  static {
    final Application app = ApplicationManager.getApplication();
    final boolean isUIPresented = app != null && !app.isHeadlessEnvironment() && !app.isUnitTestMode() && !app.isCommandLine();
    final boolean isRegistryKeyEnabled = Registry.is(ourRegistryKeyTouchbar, false);
    if (
      isUIPresented
      && isSupportedOS()
      && isRegistryKeyEnabled
      && Utils.isTouchBarServerRunning()
    ) {
      try {
        loadLibrary();
      } catch (Throwable e) {
        LOG.error("Failed to load nst library for touchbar: ", e);
      }

      if (ourNSTLibrary != null) {
        // small check that loaded library works
        try {
          final ID test = ourNSTLibrary.createTouchBar("test", (uid) -> { return ID.NIL; }, null);
          if (test == null || test == ID.NIL) {
            LOG.error("Failed to create native touchbar object, result is null");
            ourNSTLibrary = null;
          } else {
            ourNSTLibrary.releaseTouchBar(test);
            LOG.info("nst library works properly, successfully created and released native touchbar object");
          }
        } catch (Throwable e) {
          LOG.error("nst library was loaded, but can't be used: ", e);
          ourNSTLibrary = null;
        }
      } else {
        LOG.error("nst library wasn't loaded");
      }
    } else if (!isUIPresented)
      LOG.debug("unit-test mode, skip nst loading");
    else if (!isSupportedOS())
      LOG.info("OS doesn't support touchbar, skip nst loading");
    else if (!isRegistryKeyEnabled)
      LOG.info("registry key '" + ourRegistryKeyTouchbar + "' is disabled, skip nst loading");
    else
      LOG.info("touchbar-server isn't running, skip nst loading");
  }

  static NSTLibrary loadLibrary() {
    UrlClassLoader.loadPlatformLibrary("nst");

    // Set JNA to convert java.lang.String to char* using UTF-8, and match that with
    // the way we tell CF to interpret our char*
    // May be removed if we use toStringViaUTF16
    System.setProperty("jna.encoding", "UTF8");

    final Map<String, Object> nstOptions = new HashMap<>();
    return ourNSTLibrary = Native.loadLibrary("nst", NSTLibrary.class, nstOptions);
  }

  public static boolean isAvailable() { return ourNSTLibrary != null; }

  public static ID createTouchBar(String name, NSTLibrary.ItemCreator creator, String escID) {
    return ourNSTLibrary.createTouchBar(name, creator, escID); // creates autorelease-pool internally
  }

  public static void releaseTouchBar(ID tbObj) {
    ourNSTLibrary.releaseTouchBar(tbObj);
  }

  public static void setTouchBar(TouchBar tb) {
    ourNSTLibrary.setTouchBar(tb == null ? ID.NIL : tb.getNativePeer());
  }

  public static void selectItemsToShow(ID tbObj, String[] ids, int count) {
    _assertIsDispatchThread();
    ourNSTLibrary.selectItemsToShow(tbObj, ids, count); // creates autorelease-pool internally
  }

  public static void setPrincipal(ID tbObj, String uid) {
    ourNSTLibrary.setPrincipal(tbObj, uid); // creates autorelease-pool internally
  }

  public static ID createButton(String uid,
                                int buttWidth,
                                int buttFlags,
                                String text,
                                Icon icon,
                                NSTLibrary.Action action) {
    final BufferedImage img = _getImg4ByteRGBA(icon);
    final byte[] raster4ByteRGBA = _getRaster(img);
    final int w = _getImgW(img);
    final int h = _getImgH(img);
    return ourNSTLibrary.createButton(uid, buttWidth, buttFlags, text, raster4ByteRGBA, w, h, action); // called from AppKit, uses per-event autorelease-pool
  }

  public static ID createPopover(String uid,
                                 int itemWidth,
                                 String text,
                                 Icon icon,
                                 ID tbObjExpand,
                                 ID tbObjTapAndHold) {
    final BufferedImage img = _getImg4ByteRGBA(icon);
    final byte[] raster4ByteRGBA = _getRaster(img);
    final int w = _getImgW(img);
    final int h = _getImgH(img);
    return ourNSTLibrary.createPopover(uid, itemWidth, text, raster4ByteRGBA, w, h, tbObjExpand, tbObjTapAndHold); // called from AppKit, uses per-event autorelease-pool
  }

  public static ID createScrubber(String uid, int itemWidth, List<TBItemScrubber.ItemData> items) {
    final NSTLibrary.ScrubberItemData[] vals = _makeItemsArray2(items);
    return ourNSTLibrary.createScrubber(uid, itemWidth, vals, vals != null ? vals.length : 0); // called from AppKit, uses per-event autorelease-pool
  }

  public static ID createGroupItem(String uid, ID[] items, int count) {
    return ourNSTLibrary.createGroupItem(uid, items, count); // called from AppKit, uses per-event autorelease-pool
  }

  public static void updateButton(ID buttonObj,
                                  int updateOptions,
                                  int buttWidth,
                                  int buttonFlags,
                                  String text,
                                  Icon icon,
                                  NSTLibrary.Action action) {
    _assertIsDispatchThread();
    final BufferedImage img = _getImg4ByteRGBA(icon);
    final byte[] raster4ByteRGBA = _getRaster(img);
    final int w = _getImgW(img);
    final int h = _getImgH(img);
    ourNSTLibrary.updateButton(buttonObj, updateOptions, buttWidth, buttonFlags, text, raster4ByteRGBA, w, h, action); // creates autorelease-pool internally
  }

  public static void updatePopover(ID popoverObj,
                                   int itemWidth,
                                   String text,
                                   Icon icon,
                                   ID tbObjExpand, ID tbObjTapAndHold) {
    _assertIsDispatchThread();
    final BufferedImage img = _getImg4ByteRGBA(icon);
    final byte[] raster4ByteRGBA = _getRaster(img);
    final int w = _getImgW(img);
    final int h = _getImgH(img);
    ourNSTLibrary.updatePopover(popoverObj, itemWidth, text, raster4ByteRGBA, w, h, tbObjExpand, tbObjTapAndHold); // creates autorelease-pool internally
  }

  public static void updateScrubber(ID scrubObj, int itemWidth, List<TBItemScrubber.ItemData> items) {
    _assertIsDispatchThread();
    final NSTLibrary.ScrubberItemData[] vals = _makeItemsArray2(items);
    ourNSTLibrary.updateScrubber(scrubObj, itemWidth, vals, vals != null ? vals.length : 0); // creates autorelease-pool internally
  }

  private static NSTLibrary.ScrubberItemData[] _makeItemsArray2(List<TBItemScrubber.ItemData> items) {
    if (items == null)
      return null;

    final NSTLibrary.ScrubberItemData scitem = new NSTLibrary.ScrubberItemData();
    // Structure.toArray allocates a contiguous block of memory internally (each array item is inside this block)
    // note that for large arrays, this can be extremely slow
    final NSTLibrary.ScrubberItemData[] result = (NSTLibrary.ScrubberItemData[])scitem.toArray(items.size());

    int c = 0;
    for (TBItemScrubber.ItemData id : items) {
      NSTLibrary.ScrubberItemData out = result[c++];
      _fill(id, out);
    }

    return result;
  }

  private static void _fill(TBItemScrubber.ItemData from, @NotNull NSTLibrary.ScrubberItemData out) {
    if (from.myText != null) {
      final byte[] data = Native.toByteArray(from.myText, "UTF8");
      out.text = new Memory(data.length + 1);
      out.text.write(0, data, 0, data.length);
      out.text.setByte(data.length, (byte)0);
    } else
      out.text = null;

    if (from.myIcon != null) {
      final BufferedImage img = _getImg4ByteRGBA(from.myIcon);
      final byte[] raster = ((DataBufferByte)img.getRaster().getDataBuffer()).getData();
      out.raster4ByteRGBA = new Memory(raster.length);
      out.raster4ByteRGBA.write(0, raster, 0, raster.length);

      out.rasterW = img.getWidth();
      out.rasterH = img.getHeight();
    } else {
      out.raster4ByteRGBA = null;
      out.rasterW = 0;
      out.rasterH = 0;
    }

    out.action = from.myAction;
  }

  private static byte[] _getRaster(BufferedImage img) {
    if (img == null)
      return null;

    return ((DataBufferByte)img.getRaster().getDataBuffer()).getData();
  }

  private static int _getImgW(BufferedImage img) { return img == null ? 0 : img.getWidth(); }
  private static int _getImgH(BufferedImage img) { return img == null ? 0 : img.getHeight(); }

  private static BufferedImage _getImg4ByteRGBA(Icon icon, float scale) {
    if (icon == null)
      return null;

    final int w = Math.round(icon.getIconWidth()*scale);
    final int h = Math.round(icon.getIconHeight()*scale);
    final WritableRaster
      raster = Raster.createInterleavedRaster(new DataBufferByte(w * h * 4), w, h, 4 * w, 4, new int[]{0, 1, 2, 3}, (Point) null);
    final ColorModel
      colorModel = new ComponentColorModel(ColorModel.getRGBdefault().getColorSpace(), true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);
    final BufferedImage image = new BufferedImage(colorModel, raster, false, null);
    final Graphics2D g = image.createGraphics();
    g.scale(scale, scale);
    g.setComposite(AlphaComposite.SrcOver);
    icon.paintIcon(null, g, 0, 0);
    g.dispose();

    return image;
  }

  private static BufferedImage _getImg4ByteRGBA(Icon icon) {
    if (icon == null)
      return null;

    // according to https://developer.apple.com/macos/human-interface-guidelines/touch-bar/touch-bar-icons-and-images/
    // icons generally should not exceed 44px in height (36px for circular icons)
    // Ideal icon size	    36px × 36px (18pt × 18pt @2x)
    // Maximum icon size    44px × 44px (22pt × 22pt @2x)
    final float fMulX = 40/16.f;
    return _getImg4ByteRGBA(icon, fMulX);
  }

  private static void _assertIsDispatchThread() {
    final Application app = ApplicationManager.getApplication();
    if (app != null)
      app.assertIsDispatchThread();
    else
      assert SwingUtilities.isEventDispatchThread();
  }
}
