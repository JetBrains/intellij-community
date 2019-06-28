// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ide.ui.UISettings;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.ui.mac.foundation.NSDefaults;
import com.intellij.util.loader.NativeLibraryLoader;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.image.WritableRasterNative;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class NST {
  private static final Logger LOG = Logger.getInstance(NST.class);
  private static final String ourRegistryKeyTouchbar = "ide.mac.touchbar.use";
  private static NSTLibrary ourNSTLibrary = null; // NOTE: JNA is stateless (doesn't have any limitations of multi-threaded use)

  private static final String MIN_OS_VERSION = "10.12.2";
  static boolean isSupportedOS() { return SystemInfo.isMac && SystemInfo.isOsVersionAtLeast(MIN_OS_VERSION); }

  static {
    try {
      if (!isSupportedOS()) {
        LOG.info("OS doesn't support touchbar, skip nst loading");
      }
      else if (GraphicsEnvironment.isHeadless()) {
        LOG.info("The graphics environment is headless, skip nst loading");
      }
      else if (!Registry.is(ourRegistryKeyTouchbar, false)) {
        LOG.info("registry key '" + ourRegistryKeyTouchbar + "' is disabled, skip nst loading");
      }
      else if (!JnaLoader.isLoaded()) {
        LOG.info("JNA library is unavailable, skip nst loading");
      }
      else if (!Utils.isTouchBarServerRunning()) {
        LOG.info("touchbar-server isn't running, skip nst loading");
      }
      else {
        try {
          loadLibrary();
        } catch (Throwable e) {
          LOG.error("Failed to load nst library for touchbar: ", e);
        }

        if (ourNSTLibrary != null) {
          // small check that loaded library works
          try {
            final ID test = ourNSTLibrary.createTouchBar("test", (uid) -> ID.NIL, null);
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
      }

      if (ourNSTLibrary != null) {
        final String appId = Utils.getAppId();
        if (appId == null || appId.isEmpty()) {
          LOG.debug("can't obtain application id from NSBundle");
        } else if (NSDefaults.isShowFnKeysEnabled(appId)) {
          LOG.info("nst library was loaded, but user enabled fn-keys in touchbar");
          ourNSTLibrary = null;
        }
      }
    } catch (Throwable e) {
      LOG.error(e);
    }
  }

  static NSTLibrary loadLibrary() {
    NativeLibraryLoader.loadPlatformLibrary("nst");

    return ourNSTLibrary = Native.load("nst", NSTLibrary.class, Collections.singletonMap("jna.encoding", "UTF8"));
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
    final Memory raster4ByteRGBA = _getRaster(img);
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
    final Memory raster4ByteRGBA = _getRaster(img);
    final int w = _getImgW(img);
    final int h = _getImgH(img);
    return ourNSTLibrary.createPopover(uid, itemWidth, text, raster4ByteRGBA, w, h, tbObjExpand, tbObjTapAndHold); // called from AppKit, uses per-event autorelease-pool
  }

  public static ID createScrubber(String uid, int itemWidth, NSTLibrary.ScrubberDelegate delegate, NSTLibrary.ScrubberCacheUpdater updater, List<? extends TBItemScrubber.ItemData> items, int itemsCount) {
    final Memory mem = _packItems(items, 0, itemsCount);
    final ID scrubberNativePeer = ourNSTLibrary.createScrubber(uid, itemWidth, delegate, updater, mem, mem == null ? 0 : (int)mem.size()); // called from AppKit, uses per-event autorelease-pool
    return scrubberNativePeer;
  }

  public static ID createGroupItem(String uid, ID[] items) {
    return ourNSTLibrary.createGroupItem(uid, items == null || items.length == 0 ? null : items, items.length); // called from AppKit, uses per-event autorelease-pool
  }

  public static void updateButton(ID buttonObj,
                                  int updateOptions,
                                  int buttWidth,
                                  int buttonFlags,
                                  String text,
                                  Icon icon,
                                  NSTLibrary.Action action) {
    final BufferedImage img = _getImg4ByteRGBA(icon);
    final Memory raster4ByteRGBA = _getRaster(img);
    final int w = _getImgW(img);
    final int h = _getImgH(img);
    ourNSTLibrary.updateButton(buttonObj, updateOptions, buttWidth, buttonFlags, text, raster4ByteRGBA, w, h, action); // creates autorelease-pool internally
  }

  public static void setArrowImage(ID buttObj, @Nullable Icon arrow) {
    final BufferedImage img = _getImg4ByteRGBA(arrow);
    final Memory raster4ByteRGBA = _getRaster(img);
    final int w = _getImgW(img);
    final int h = _getImgH(img);
    ourNSTLibrary.setArrowImage(buttObj, raster4ByteRGBA, w, h); // creates autorelease-pool internally
  }

  public static void updatePopover(ID popoverObj,
                                   int itemWidth,
                                   String text,
                                   Icon icon,
                                   ID tbObjExpand, ID tbObjTapAndHold) {
    final BufferedImage img = _getImg4ByteRGBA(icon);
    final Memory raster4ByteRGBA = _getRaster(img);
    final int w = _getImgW(img);
    final int h = _getImgH(img);
    ourNSTLibrary.updatePopover(popoverObj, itemWidth, text, raster4ByteRGBA, w, h, tbObjExpand, tbObjTapAndHold); // creates autorelease-pool internally
  }

  public static void updateScrubber(ID scrubObj, int itemWidth, List<? extends TBItemScrubber.ItemData> items) {
    LOG.error("updateScrubber musn't be called");
  }

  private static Memory _makeIndices(Collection<Integer> indices) {
    if (indices == null || indices.isEmpty())
      return null;
    final int step = Native.getNativeSize(Integer.class);
    final Memory mem = new Memory(indices.size()*step);
    int offset = 0;
    for (Integer i: indices) {
      mem.setInt(offset, i);
      offset += step;
    }
    return mem;
  }

  static void appendScrubberItems(ID scrubObj, List<? extends TBItemScrubber.ItemData> items, int fromIndex, int itemsCount) {
    final Memory mem = _packItems(items, fromIndex, itemsCount);
    ourNSTLibrary.appendScrubberItems(scrubObj, mem, mem == null ? 0 : (int)mem.size()); // called from AppKit, uses per-event autorelease-pool
  }
  public static void enableScrubberItem(ID scrubObj, Collection<Integer> indices, boolean enabled) {
    if (indices == null || indices.isEmpty())
      return;
    final Memory mem = _makeIndices(indices);
    ourNSTLibrary.enableScrubberItems(scrubObj, mem, indices.size(), enabled);
  }
  public static void showScrubberItem(ID scrubObj, Collection<Integer> indices, boolean show) {
    if (indices == null || indices.isEmpty())
      return;
    final Memory mem = _makeIndices(indices);
    ourNSTLibrary.showScrubberItems(scrubObj, mem, indices.size(), show);
  }

  private static @Nullable Memory _packItems(List<? extends TBItemScrubber.ItemData> items, int fromIndex, int itemsCount) {
    if (items == null || itemsCount <= 0)
      return null;
    if (fromIndex < 0) {
      LOG.error("_packItems: fromIndex < 0 (" + fromIndex + ")");
      return null;
    }
    if (fromIndex + itemsCount > items.size()) {
      LOG.error("_packItems: fromIndex + itemsCount > items.size() (" + fromIndex + ", " + itemsCount + ", " + items.size() + ")");
      return null;
    }

    // 1. calculate size
    int byteCount = 4;
    for (int c = 0; c < itemsCount; ++c) {
      TBItemScrubber.ItemData id = items.get(fromIndex + c);
      byteCount += 4 + (id.getTextBytes() != null ? id.getTextBytes().length + 1 : 0);

      if (id.myIcon != null) {
        final int w = Math.round(id.myIcon.getIconWidth()*id.fMulX);
        final int h = Math.round(id.myIcon.getIconHeight()*id.fMulX);
        final int sizeInBytes = w * h * 4;
        final int totalSize = sizeInBytes + 8;
        byteCount += totalSize;
      } else
        byteCount += 4;
    }

    // 2. write items
    final Memory result = new Memory(byteCount + 200);
    result.setInt(0, itemsCount);
    int offset = 4;
    for (int c = 0; c < itemsCount; ++c) {
      TBItemScrubber.ItemData id = items.get(fromIndex + c);
      offset = _fill(id, result, offset);
    }

    return result;
  }

  private static int _fill(@NotNull TBItemScrubber.ItemData from, @NotNull Memory out, int offset) {
    if (from.getTextBytes() != null) {
      out.setInt(offset, from.getTextBytes().length);
      offset += 4;
      out.write(offset, from.getTextBytes(), 0, from.getTextBytes().length);
      offset += from.getTextBytes().length;
      out.setByte(offset, (byte)0);
      offset += 1;
    } else {
      out.setInt(offset, 0);
      offset += 4;
    }

    if (from.myIcon != null) {
      offset += _writeIconRaster(from.myIcon, from.fMulX, out, offset);
    } else {
      out.setInt(offset, 0);
      out.setInt(offset +4, 0);
      offset += 8;
    }

    return offset;
  }

  private static Memory _getRaster(BufferedImage img) {
    if (img == null)
      return null;

    final DataBuffer db = img.getRaster().getDataBuffer();
    DirectDataBufferInt dbb = (DirectDataBufferInt)db;
    return dbb.myMemory;
  }

  private static int _getImgW(BufferedImage img) { return img == null ? 0 : img.getWidth(); }
  private static int _getImgH(BufferedImage img) { return img == null ? 0 : img.getHeight(); }

  private static BufferedImage _getImg4ByteRGBA(Icon icon, float scale) {
    if (icon == null)
      return null;

    final int w = Math.round(icon.getIconWidth()*scale);
    final int h = Math.round(icon.getIconHeight()*scale);

    Memory memory = new Memory(w*h*4);
    return _drawIconIntoMemory(icon, scale, memory, 0);
  }

  private static BufferedImage _getImg4ByteRGBA(Icon icon) {
    if (icon == null || icon.getIconHeight() == 0)
      return null;

    // according to https://developer.apple.com/macos/human-interface-guidelines/touch-bar/touch-bar-icons-and-images/
    // icons generally should not exceed 44px in height (36px for circular icons)
    // Ideal icon size	    36px X 36px (18pt X 18pt @2x)
    // Maximum icon size    44px X 44px (22pt X 22pt @2x)

    final Application app = ApplicationManager.getApplication();
    final float fMulX = app != null && UISettings.getInstance().getPresentationMode() ? 40.f/icon.getIconHeight() : (icon.getIconHeight() < 24 ? 40.f/16 : 44.f/icon.getIconHeight());
    return _getImg4ByteRGBA(icon, fMulX);
  }

  // returns count of written bytes
  private static int _writeIconRaster(@NotNull Icon icon, float scale, @NotNull Memory memory, int offset) {
    final int w = Math.round(icon.getIconWidth()*scale);
    final int h = Math.round(icon.getIconHeight()*scale);

    final int rasterSizeInBytes = w * h * 4;
    final int totalSize = rasterSizeInBytes + 8;
    if (memory.size() - offset < totalSize) {
      LOG.error("insufficient size of allocated memory: avail " + (memory.size() - offset) + ", needs " + totalSize);
      return 0;
    }

    memory.setInt(offset, w);
    offset += 4;
    memory.setInt(offset, h);
    offset += 4;

    _drawIconIntoMemory(icon, scale, memory, offset);

    return totalSize;
  }

  // returns count of written bytes
  private static BufferedImage _drawIconIntoMemory(@NotNull Icon icon, float scale, @NotNull Memory memory, int offset) {
    final int w = Math.round(icon.getIconWidth()*scale);
    final int h = Math.round(icon.getIconHeight()*scale);
    final int rasterSizeInBytes = w * h * 4;

    memory.setMemory(offset, rasterSizeInBytes, (byte)0);

    DataBuffer dataBuffer = new DirectDataBufferInt(memory, offset);
    final DirectColorModel colorModel = new DirectColorModel(ColorModel.getRGBdefault().getColorSpace(), 32, 0xFF, 0xFF00, 0x00FF0000, 0xff000000/*alpha*/, false, DataBuffer.TYPE_INT);
    final SampleModel sm = colorModel.createCompatibleSampleModel(w, h);
    final WritableRaster raster = WritableRasterNative.createNativeRaster(sm, dataBuffer);
    final BufferedImage image = new BufferedImage(colorModel, raster, false, null);

    final Graphics2D g = image.createGraphics();
    g.scale(scale, scale);
    g.setComposite(AlphaComposite.SrcOver);
    icon.paintIcon(null, g, 0, 0);
    g.dispose();

    return image;
  }
}

class DirectDataBufferInt extends DataBuffer {
  protected Memory myMemory;
  private final int myOffset;

  public DirectDataBufferInt(Memory memory, int offset) {
    super(TYPE_INT, (int)memory.size());
    this.myMemory = memory;
    this.myOffset = offset;
  }
  public int getElem(int bank, int i) {
    return myMemory.getInt(myOffset + i*4); // same as: *((jint *)((char *)Pointer + offset))
  }
  public void setElem(int bank, int i, int val) {
    myMemory.setInt(myOffset + i*4, val); // same as: *((jint *)((char *)Pointer + offset)) = value
  }
}

class DirectDataBufferByte extends DataBuffer {
  protected Memory myMemory;
  private final int myOffset;

  public DirectDataBufferByte(Memory mem, int offset) {
    super(TYPE_BYTE, (int)mem.size());
    this.myMemory = mem;
    this.myOffset = offset;
  }
  public int getElem(int bank, int i) {
    return myMemory.getByte(myOffset + i); // same as: *((jbyte *)((char *)Pointer + offset))
  }
  public void setElem(int bank, int i, int val) {
    myMemory.setByte(myOffset + i, (byte)val); // same as: *((jbyte *)((char *)Pointer + offset)) = value
  }
}