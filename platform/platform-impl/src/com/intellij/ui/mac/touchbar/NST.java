// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ide.ui.UISettings;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.EmptyIcon;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;
import sun.awt.image.WritableRasterNative;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.peer.ComponentPeer;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class NST {
  private static final Logger LOG = Logger.getInstance(NST.class);
  private static final String ourRegistryKeyTouchbar = "ide.mac.touchbar.use";
  private static NSTLibrary ourNSTLibrary = null; // NOTE: JNA is stateless (doesn't have any limitations of multi-threaded use)

  private static final String MIN_OS_VERSION = "10.12.2";

  static boolean isSupportedOS() {
    return SystemInfo.isMac && SystemInfo.isOsVersionAtLeast(MIN_OS_VERSION);
  }

  static void loadLibrary() {
    try {
      if (!isSupportedOS()) {
        LOG.info("OS doesn't support touchbar, skip nst loading");
      }
      else if (GraphicsEnvironment.isHeadless()) {
        LOG.info("The graphics environment is headless, skip nst loading");
      }
      else if (!SystemProperties.getBooleanProperty(ourRegistryKeyTouchbar, true)) {
        LOG.info("system property '" + ourRegistryKeyTouchbar + "' is set to false, skip nst loading");
      }
      else if (!JnaLoader.isLoaded()) {
        LOG.info("JNA library is unavailable, skip nst loading");
      }
      else if (!Helpers.isTouchBarServerRunning()) {
        LOG.info("touchbar-server isn't running, skip nst loading");
      }
      else {
        try {
          loadLibraryImpl();
        }
        catch (Throwable e) {
          LOG.error("Failed to load nst library for touchbar: ", e);
        }

        if (ourNSTLibrary != null) {
          // small check that loaded library works
          try {
            final ID test = ourNSTLibrary.createTouchBar("test", (uid) -> ID.NIL, null);
            if (test == null || test.equals(ID.NIL)) {
              LOG.error("Failed to create native touchbar object, result is null");
              ourNSTLibrary = null;
            }
            else {
              ourNSTLibrary.releaseNativePeer(test);
              LOG.info("nst library works properly, successfully created and released native touchbar object");
            }
          }
          catch (Throwable e) {
            LOG.error("nst library was loaded, but can't be used: ", e);
            ourNSTLibrary = null;
          }
        }
        else {
          LOG.error("nst library wasn't loaded");
        }
      }
    }
    catch (Throwable e) {
      LOG.error(e);
    }
  }

  static NSTLibrary loadLibraryImpl() {
    Path lib = PathManager.findBinFile("libnst64.dylib");
    assert lib != null : "NST lib missing; bin=" + Arrays.toString(new File(PathManager.getBinPath()).list());
    return ourNSTLibrary = Native.load(lib.toString(), NSTLibrary.class, Collections.singletonMap("jna.encoding", "UTF8"));
  }

  static boolean isAvailable() {
    return ourNSTLibrary != null;
  }

  static ID createTouchBar(String name, NSTLibrary.ItemCreator creator, String escID) {
    return ourNSTLibrary.createTouchBar(name, creator, escID); // creates autorelease-pool internally
  }

  static void releaseNativePeer(ID nativePeer) {
    ourNSTLibrary.releaseNativePeer(nativePeer);
  }

  static void setTouchBar(@Nullable Window window, ID touchBarNativePeer) {
    long nsViewPtr = 0;
    if (window != null) {
      final ComponentPeer peer = AWTAccessor.getComponentAccessor().getPeer(window);

      // sun.lwawt.* isn't available outside of java.desktop => use reflection
      if (peer != null && peer.getClass().getName().equals("sun.lwawt.LWWindowPeer")) {
        final Method methodGetPlatformWindow;
        try {
          methodGetPlatformWindow = peer.getClass().getMethod("getPlatformWindow");
          Object platformWindow = methodGetPlatformWindow.invoke(peer);
          if (platformWindow != null && platformWindow.getClass().getName().equals("sun.lwawt.macosx.CPlatformWindow")) {
            final Method methodGetContentView = platformWindow.getClass().getMethod("getContentView");
            Object contentView = methodGetContentView.invoke(platformWindow);
            final Method methodGetAWTView = contentView.getClass().getMethod("getAWTView");
            nsViewPtr = (long)methodGetAWTView.invoke(contentView);
          } else {
            LOG.debug("platformWindow of frame peer isn't instance of sun.lwawt.macosx.CPlatformWindow, class of platformWindow: %s",
                      platformWindow != null ? platformWindow.getClass() : "null");
          }
        }
        catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
          LOG.debug(e);
        }
      }
      else {
        if (peer == null)
          LOG.debug("frame peer is null, window: %s", window);
        else
          LOG.debug("frame peer isn't instance of sun.lwawt.LWWindowPeer, class of peer: %s", peer.getClass());
      }
    }
    ourNSTLibrary.setTouchBar(new ID(nsViewPtr), touchBarNativePeer);
  }

  static void selectItemsToShow(ID tbObj, String[] ids, int count) {
    ourNSTLibrary.selectItemsToShow(tbObj, ids, count); // creates autorelease-pool internally
  }

  static void setPrincipal(ID tbObj, String uid) {
    ourNSTLibrary.setPrincipal(tbObj, uid); // creates autorelease-pool internally
  }

  static ID createButton(String uid,
                                int buttWidth,
                                int buttFlags,
                                String text,
                                String hint, int isHintDisabled,
                                @Nullable Pair<Pointer, Dimension> raster,
                                NSTLibrary.Action action) {
    return ourNSTLibrary.createButton(
      uid, buttWidth, buttFlags,
      text, hint,
      isHintDisabled,
      raster == null ? null : raster.getFirst(),
      raster == null ? 0 : raster.getSecond().width,
      raster == null ? 0 : raster.getSecond().height,
      action); // called from AppKit, uses per-event autorelease-pool
  }

  // NOTE: due to optimization scrubber is created without icons
  // icons must be updated async via updateScrubberItems
  @SuppressWarnings("unused")
  static ID createScrubber(
    String uid, int itemWidth, NSTLibrary.ScrubberDelegate delegate, NSTLibrary.ScrubberCacheUpdater updater,
    @NotNull List<? extends TBItemScrubber.ItemData> items, int visibleItems, @Nullable TouchBarStats stats
  ) {
    final Pair<Pointer, Integer> mem = _packItems(items, visibleItems, false, true);
    return ourNSTLibrary.createScrubber(uid, itemWidth, delegate, updater, mem == null ? null : mem.getFirst(), mem == null ? 0 : mem.getSecond()); // called from AppKit, uses per-event autorelease-pool
  }

  static ID createGroupItem(String uid, ID[] items) {
    return ourNSTLibrary.createGroupItem(uid, items == null || items.length == 0 ? null : items, items == null ? 0 : items.length); // called from AppKit, uses per-event autorelease-pool
  }

  static void updateButton(ID buttonObj,
                                  int updateOptions,
                                  int buttWidth,
                                  int buttonFlags,
                                  String text,
                                  String hint, int isHintDisabled,
                                  @Nullable Pair<Pointer, Dimension> raster,
                                  NSTLibrary.Action action) {
    ourNSTLibrary.updateButton(
      buttonObj, updateOptions,
      buttWidth, buttonFlags,
      text,
      hint, isHintDisabled,
      raster == null ? null : raster.getFirst(),
      raster == null ? 0 : raster.getSecond().width,
      raster == null ? 0 : raster.getSecond().height,
      action); // creates autorelease-pool internally
  }

  static void setArrowImage(ID buttObj, @Nullable Icon arrow) {
    final BufferedImage img = _getImg4ByteRGBA(arrow);
    final Pointer raster4ByteRGBA = _getRaster(img);
    final int w = _getImgW(img);
    final int h = _getImgH(img);
    ourNSTLibrary.setArrowImage(buttObj, raster4ByteRGBA, w, h); // creates autorelease-pool internally
  }

  private static Pointer _makeIndices(Collection<Integer> indices) {
    if (indices == null || indices.isEmpty())
      return null;
    final int step = Native.getNativeSize(Integer.class);
    final Pointer mem = new Pointer(Native.malloc((long)indices.size() * step));
    int offset = 0;
    for (Integer i: indices) {
      mem.setInt(offset, i);
      offset += step;
    }
    return mem;
  }

  static void updateScrubberItems(
    TBItemScrubber scrubber, int fromIndex, int itemsCount,
    boolean withImages, boolean withText
  ) {
    final long startNs = withImages && scrubber.getStats() != null ? System.nanoTime() : 0;
    @NotNull List<TBItemScrubber.ItemData> items = scrubber.getItems();
    final Pair<Pointer, Integer> mem = _packItems(items.subList(fromIndex, fromIndex + itemsCount), itemsCount, withImages, withText);
    synchronized (scrubber) {
      if (scrubber.myNativePeer.equals(ID.NIL))
        return;
      ourNSTLibrary.updateScrubberItems(scrubber.myNativePeer, mem == null ? null : mem.getFirst(), mem == null ? 0 : mem.getSecond(), fromIndex);
    }
    if (withImages && scrubber.getStats() != null)
      scrubber.getStats().incrementCounter(StatsCounters.scrubberIconsProcessingDurationNs, System.nanoTime() - startNs);
  }
  static void enableScrubberItems(ID scrubObj, Collection<Integer> indices, boolean enabled) {
    if (indices == null || indices.isEmpty() || scrubObj == ID.NIL || scrubObj == null)
      return;
    final Pointer mem = _makeIndices(indices);
    ourNSTLibrary.enableScrubberItems(scrubObj, mem, indices.size(), enabled);
  }
  static void showScrubberItem(ID scrubObj, Collection<Integer> indices, boolean show, boolean inverseOthers) {
    if (scrubObj == ID.NIL || scrubObj == null)
      return;
    final Pointer mem = _makeIndices(indices);
    ourNSTLibrary.showScrubberItems(scrubObj, mem, indices == null ? 0 : indices.size(), show, inverseOthers);
  }

  private static @Nullable Pair<Pointer, Integer> _packItems(
    @NotNull List<? extends TBItemScrubber.ItemData> items,
    int visibleItems, boolean withImages, boolean withText
  ) {
    if (items.isEmpty())
      return null;

    long ptr = 0;
    try {
      // 1. calculate size
      int byteCount = 2; // first 2 bytes contains count of items
      int c = 0;
      for (TBItemScrubber.ItemData id: items) {
        if (c++ >= visibleItems) {
          byteCount += 6;
          continue;
        }
        final int textSize = 2 + (withText && id.getTextBytes() != null && id.getTextBytes().length > 0 ? id.getTextBytes().length + 1 : 0);
        byteCount += textSize;

        if (withImages
            && id.darkIcon == null
            && id.getIcon() != null
            && !(id.getIcon() instanceof EmptyIcon)
            && id.getIcon().getIconWidth() > 0
            && id.getIcon().getIconHeight() > 0
        ) {
          id.darkIcon = ReadAction.nonBlocking(() -> IconLoader.getDarkIcon(id.getIcon(), true)).executeSynchronously();
          if (id.darkIcon != null && (id.darkIcon.getIconWidth() <= 0 || id.darkIcon.getIconHeight() <= 0)) {
            LOG.debug("Can't obtain dark icon for scrubber item '%s', use default icon", id.getText());
            id.darkIcon = id.getIcon();
          }
        }

        if (withImages && id.darkIcon != null) {
          id.fMulX = getIconScaleForTouchbar(id.darkIcon);
          id.scaledWidth = Math.round(id.darkIcon.getIconWidth()*id.fMulX);
          id.scaledHeight = Math.round(id.darkIcon.getIconHeight()*id.fMulX);
          final int sizeInBytes = id.scaledWidth * id.scaledHeight * 4;
          final int totalSize = sizeInBytes + 4;
          byteCount += totalSize;
        } else
          byteCount += 4;
      }

      // 2. write items
      final Pointer result = new Pointer(ptr = Native.malloc(byteCount));
      result.setShort(0, (short)items.size());
      int offset = 2;
      c = 0;
      for (TBItemScrubber.ItemData id: items) {
        if (c++ >= visibleItems) {
          result.setShort(offset, (short)0);
          result.setShort(offset + 2, (short)0);
          result.setShort(offset + 4, (short)0);
          offset += 6;
          continue;
        }

        final byte[] txtBytes = withText ? id.getTextBytes() : null;
        if (txtBytes != null && txtBytes.length > 0) {
          result.setShort(offset, (short)txtBytes.length);
          offset += 2;
          result.write(offset, txtBytes, 0, txtBytes.length);
          offset += txtBytes.length;
          result.setByte(offset, (byte)0);
          offset += 1;
        } else {
          result.setShort(offset, (short)0);
          offset += 2;
        }

        if (withImages && id.darkIcon != null) {
          offset += _writeIconRaster(id.darkIcon, id.fMulX, result, offset, byteCount);
        } else {
          final boolean hasIcon = id.getIcon() != null && !(id.getIcon() instanceof EmptyIcon) && id.getIcon().getIconWidth() > 0 && id.getIcon().getIconHeight() > 0;
          result.setShort(offset, hasIcon ? (short) 1: (short)0);
          result.setShort(offset + 2, (short)0);
          offset += 4;
        }
      }

      return Pair.create(result, byteCount);
    } catch (Throwable e) {
      if (ptr != 0) {
        Native.free(ptr);
      }
      LOG.debug(e);
      return null;
    }
  }

  static Pair<Pointer, Dimension> get4ByteRGBARaster(@Nullable Icon icon) {
    if (icon == null || icon.getIconHeight() <= 0 || icon.getIconWidth() <= 0)
      return null;

    final float fMulX = getIconScaleForTouchbar(icon);
    final @NotNull BufferedImage img = _getImg4ByteRGBA(icon, fMulX);
    return Pair.create(_getRaster(img), new Dimension(img.getWidth(), img.getHeight()));
  }

  private static Pointer _getRaster(BufferedImage img) {
    if (img == null)
      return null;

    final DataBuffer db = img.getRaster().getDataBuffer();
    DirectDataBufferInt dbb = (DirectDataBufferInt)db;
    return dbb.myMemory;
  }

  private static int _getImgW(BufferedImage img) { return img == null ? 0 : img.getWidth(); }
  private static int _getImgH(BufferedImage img) { return img == null ? 0 : img.getHeight(); }

  private static BufferedImage _getImg4ByteRGBA(Icon icon, float scale) {
    if (icon == null || icon.getIconHeight() <= 0 || icon.getIconWidth() <= 0)
      return null;

    final int w = Math.round(icon.getIconWidth()*scale);
    final int h = Math.round(icon.getIconHeight()*scale);

    final int memLength = w*h*4;
    Pointer memory = new Memory(memLength);
    return _drawIconIntoMemory(icon, scale, memory, 0);
  }

  private static float getIconScaleForTouchbar(@NotNull Icon icon) {
    // according to https://developer.apple.com/macos/human-interface-guidelines/touch-bar/touch-bar-icons-and-images/
    // icons generally should not exceed 44px in height (36px for circular icons)
    // Ideal icon size	    36px X 36px (18pt X 18pt @2x)
    // Maximum icon size    44px X 44px (22pt X 22pt @2x)
    return UISettings.getInstance().getPresentationMode() ?
           40.f/icon.getIconHeight() :
           (icon.getIconHeight() < 24 ? 40.f/16 : 44.f/icon.getIconHeight());
  }

  private static BufferedImage _getImg4ByteRGBA(@Nullable Icon icon) {
    if (icon == null || icon.getIconHeight() <= 0 || icon.getIconWidth() <= 0)
      return null;

    final float fMulX = getIconScaleForTouchbar(icon);
    return _getImg4ByteRGBA(icon, fMulX);
  }

  // returns count of written bytes
  private static int _writeIconRaster(@NotNull Icon icon, float scale, @NotNull Pointer memory, int offset, int totalMemoryBytes) throws Exception {
    final int w = Math.round(icon.getIconWidth()*scale);
    final int h = Math.round(icon.getIconHeight()*scale);

    if (w <= 0 || h <= 0) {
      throw new Exception("Incorrect icon sizes: " + icon.getIconWidth() + "x" + icon.getIconHeight() + ", scale=" + scale);
    }

    final int rasterSizeInBytes = w * h * 4;
    final int totalSize = rasterSizeInBytes + 4;

    if (offset + totalSize > totalMemoryBytes) {
      throw new Exception("Incorrect memory offset: offset=" + offset + ", rasterSize=" + rasterSizeInBytes + ", totalMemoryBytes=" + totalMemoryBytes);
    }

    memory.setShort(offset, (short)w);
    offset += 2;
    memory.setShort(offset, (short)h);
    offset += 2;

    _drawIconIntoMemory(icon, scale, memory, offset);

    return totalSize;
  }

  // returns count of written bytes
  private static @NotNull BufferedImage _drawIconIntoMemory(@NotNull Icon icon, float scale, @NotNull Pointer memory, int offset) {
    final int w = Math.round(icon.getIconWidth()*scale);
    final int h = Math.round(icon.getIconHeight()*scale);
    final int rasterSizeInBytes = w * h * 4;

    memory.setMemory(offset, rasterSizeInBytes, (byte)0);

    DataBuffer dataBuffer = new DirectDataBufferInt(memory, rasterSizeInBytes, offset);
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
  protected Pointer myMemory;
  private final int myOffset;

  DirectDataBufferInt(Pointer memory, int memLength, int offset) {
    super(TYPE_INT, memLength);
    this.myMemory = memory;
    this.myOffset = offset;
  }
  @Override
  public int getElem(int bank, int i) {
    return myMemory.getInt(myOffset + i * 4L); // same as: *((jint *)((char *)Pointer + offset))
  }
  @Override
  public void setElem(int bank, int i, int val) {
    myMemory.setInt(myOffset + i * 4L, val); // same as: *((jint *)((char *)Pointer + offset)) = value
  }
}

@SuppressWarnings("unused")
class DirectDataBufferByte extends DataBuffer {
  protected Pointer myMemory;
  private final int myOffset;

  DirectDataBufferByte(Pointer mem, int memLength, int offset) {
    super(TYPE_BYTE, memLength);
    this.myMemory = mem;
    this.myOffset = offset;
  }
  @Override
  public int getElem(int bank, int i) {
    return myMemory.getByte(myOffset + i); // same as: *((jbyte *)((char *)Pointer + offset))
  }
  @Override
  public void setElem(int bank, int i, int val) {
    myMemory.setByte(myOffset + i, (byte)val); // same as: *((jbyte *)((char *)Pointer + offset)) = value
  }
}
