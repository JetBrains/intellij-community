// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.application.ApplicationManager;
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
  private static final String ourRegistryKeyTouchbar = "ide.mac.touchbar.use";
  private static final String ourRegistryKeyDoCheckEnv = "ide.mac.touchbar.check.env";
  private static final NSTLibrary ourNSTLibrary; // NOTE: JNA is stateless (doesn't have any limitations of multi-threaded use)

  static {
    final boolean isSystemSupportTouchbar = SystemInfo.isMac && SystemInfo.isOsVersionAtLeast("10.12.2");
    final boolean isRegistryKeyEnabled = Registry.is(ourRegistryKeyTouchbar, false);
    final boolean doCheckEnv = Registry.is(ourRegistryKeyDoCheckEnv, true);
    NSTLibrary lib = null;
    if (
      isSystemSupportTouchbar
      && isRegistryKeyEnabled
      && (!doCheckEnv || SystemSettingsTouchBar.isTouchBarServerRunning())
    ) {
      try {
        UrlClassLoader.loadPlatformLibrary("nst");

        // Set JNA to convert java.lang.String to char* using UTF-8, and match that with
        // the way we tell CF to interpret our char*
        // May be removed if we use toStringViaUTF16
        System.setProperty("jna.encoding", "UTF8");

        final Map<String, Object> nstOptions = new HashMap<>();
        lib = Native.loadLibrary("nst", NSTLibrary.class, nstOptions);
      } catch (RuntimeException e) {
        LOG.error("Failed to load nst library for touchbar: ", e);
      }

      if (lib != null) {
        // small check that loaded library works
        try {
          final ID test = lib.createTouchBar("test", (uid) -> { return ID.NIL; }, null);
          if (test == null || test == ID.NIL) {
            LOG.error("Failed to create native touchbar object, result is null");
          } else {
            lib.releaseTouchBar(test);
            LOG.info("nst library works properly, successfully created and released native touchbar object");
          }
        } catch (RuntimeException e) {
          LOG.error("nst library was loaded, but can't be used: ", e);
        }
      } else {
        LOG.error("nst library wasn't loaded");
      }
    } else if (!isSystemSupportTouchbar)
      LOG.info("OS doesn't support touchbar, skip nst loading");
    else if (!isRegistryKeyEnabled)
      LOG.info("registry key '" + ourRegistryKeyTouchbar + "' is disabled, skip nst loading");
    else
      LOG.warn("touchbar-server isn't running, skip nst loading");

    ourNSTLibrary = lib;
  }

  public static boolean isAvailable() { return ourNSTLibrary != null; }

  public static ID createTouchBar(String name, NSTLibrary.ItemCreator creator, String escID) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return ourNSTLibrary.createTouchBar(name, creator, escID);
  }

  public static void releaseTouchBar(ID tbObj) {
    ourNSTLibrary.releaseTouchBar(tbObj);
  }

  public static void setTouchBar(TouchBar tb) {
    ourNSTLibrary.setTouchBar(tb == null ? ID.NIL : tb.getNativePeer());
  }

  public static void selectItemsToShow(ID tbObj, String[] ids, int count) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ourNSTLibrary.selectItemsToShow(tbObj, ids, count);
  }

  public static void setPrincipal(ID tbObj, String uid) {
    ourNSTLibrary.setPrincipal(tbObj, uid);
  }

  public static ID createButton(String uid,
                                int buttWidth,
                                int buttFlags,
                                String text,
                                byte[] raster4ByteRGBA,
                                int w,
                                int h,
                                NSTLibrary.Action action) {
    return ourNSTLibrary.createButton(uid, buttWidth, buttFlags, text, raster4ByteRGBA, w, h, action);
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
                                  NSTLibrary.ScrubberItemData[] items,
                                  int count) {
    return ourNSTLibrary.createScrubber(uid, itemWidth, items, count);
  }

  public static ID createGroupItem(String uid, ID[] items, int count) {
    return ourNSTLibrary.createGroupItem(uid, items, count);
  }

  public static void updateButton(ID buttonObj,
                                  int updateOptions,
                                  int buttWidth,
                                  int buttonFlags,
                                  String text,
                                  byte[] raster4ByteRGBA,
                                  int w,
                                  int h,
                                  NSTLibrary.Action action) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ourNSTLibrary.updateButton(buttonObj, updateOptions, buttWidth, buttonFlags, text, raster4ByteRGBA, w, h, action);
  }

  public static void updatePopover(ID popoverObj,
                                   int itemWidth,
                                   String text,
                                   byte[] raster4ByteRGBA,
                                   int w,
                                   int h,
                                   ID tbObjExpand, ID tbObjTapAndHold) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ourNSTLibrary.updatePopover(popoverObj, itemWidth, text, raster4ByteRGBA, w, h, tbObjExpand, tbObjTapAndHold);
  }

  public static void updateScrubber(ID scrubObj, int itemWidth, NSTLibrary.ScrubberItemData[] items, int count) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ourNSTLibrary.updateScrubber(scrubObj, itemWidth, items, count);
  }
}
