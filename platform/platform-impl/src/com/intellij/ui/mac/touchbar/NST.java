// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.lang.UrlClassLoader;
import com.sun.jna.Native;

import java.util.HashMap;
import java.util.Map;

public class NST {
  private static final Logger LOG = Logger.getInstance(NST.class);
  private static final NSTLibrary ourNSTLibrary;

  static {
    final boolean isSystemSupportTouchbar = SystemInfo.isMac && SystemInfo.isOsVersionAtLeast("10.12.2");
    NSTLibrary lib = null;
    if (isSystemSupportTouchbar && Registry.is("ide.mac.touchbar.use", false) && SystemSettingsTouchBar.isTouchBarServerRunning()) {
      try {
        UrlClassLoader.loadPlatformLibrary("nst");

        // Set JNA to convert java.lang.String to char* using UTF-8, and match that with
        // the way we tell CF to interpret our char*
        // May be removed if we use toStringViaUTF16
        System.setProperty("jna.encoding", "UTF8");

        final Map<String, Object> nstOptions = new HashMap<>();
        lib = Native.loadLibrary("nst", NSTLibrary.class, nstOptions);
      } catch (Throwable e) {
        LOG.error("Failed to load nst library for touchbar: ", e);
      }
    }
    ourNSTLibrary = lib;
  }

  public static boolean isAvailable() { return ourNSTLibrary != null; }

  //
  // TODO: syncronization between AppKit and EDT will be implemented in next commit
  //

  public static ID createTouchBar(String name, NSTLibrary.ItemCreator creator) {
    return ourNSTLibrary.createTouchBar(name, creator);
  }

  public static void releaseTouchBar(ID tbObj) {
    ourNSTLibrary.releaseTouchBar(tbObj);
  }

  public static void setTouchBar(TouchBar tb) {
    ourNSTLibrary.setTouchBar(tb == null ? ID.NIL : tb.getNativePeer());
  }

  public static void selectItemsToShow(ID tbObj, String[] ids, int count) {
    ourNSTLibrary.selectItemsToShow(tbObj, ids, count);
  }

  public static ID createButton(String uid,
                                int buttWidth,
                                String text,
                                byte[] raster4ByteRGBA,
                                int w,
                                int h,
                                NSTLibrary.Action action) {
    return ourNSTLibrary.createButton(uid, buttWidth, text, raster4ByteRGBA, w, h, action);
  }

  public static ID createPopover(String uid,
                                 int itemWidth,
                                 String text,
                                 byte[] raster4ByteRGBA,
                                 int w,
                                 int h,
                                 ID tbObjExpand,
                                 ID tbObjTapAndHold) {
    return ourNSTLibrary.createPopover(uid, itemWidth, text, raster4ByteRGBA, w, h, tbObjExpand, tbObjTapAndHold);
  }

  public static ID createScrubber(String uid,
                                  int itemWidth,
                                  NSTLibrary.ScrubberItemsSource source,
                                  NSTLibrary.ScrubberItemsCount count,
                                  NSTLibrary.ActionWithIndex actions) {
    return ourNSTLibrary.createScrubber(uid, itemWidth, source, count, actions);
  }

  public static void updateButton(ID buttonObj,
                                  int buttWidth,
                                  String text,
                                  byte[] raster4ByteRGBA,
                                  int w,
                                  int h,
                                  NSTLibrary.Action action) {
    ourNSTLibrary.updateButton(buttonObj, buttWidth, text, raster4ByteRGBA, w, h, action);
  }

  public static void updatePopover(ID popoverObj,
                                   int itemWidth,
                                   String text,
                                   byte[] raster4ByteRGBA,
                                   int w,
                                   int h,
                                   ID tbObjExpand, ID tbObjTapAndHold) {
    ourNSTLibrary.updatePopover(popoverObj, itemWidth, text, raster4ByteRGBA, w, h, tbObjExpand, tbObjTapAndHold);
  }
}
