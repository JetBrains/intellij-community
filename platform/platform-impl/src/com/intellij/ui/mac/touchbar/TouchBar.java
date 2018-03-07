// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ide.TBItemCallback;
import com.intellij.ide.NSTLibrary;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.lang.UrlClassLoader;
import com.sun.jna.Native;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class TouchBar {
  private static final Logger ourLog = Logger.getInstance(TouchBar.class);

  private static final NSTLibrary ourNSTLibrary;
  private static final Map<String, TBItemCallback> ourActions = new HashMap<>();

  static {
    // TODO: check macOS version with use of CoreServices:
    // SInt32 major, minor, bugfix;
    // Gestalt(gestaltSystemVersionMajor, &major);
    // Gestalt(gestaltSystemVersionMinor, &minor);
    // Gestalt(gestaltSystemVersionBugFix, &bugfix);
    // NSString *systemVersion = [NSString stringWithFormat:@"%d.%d.%d", major, minor, bugfix];

    // NOTE: can also check existence of process 'ControlStrip' to determine touchbar availability

    final boolean isTouchbarAvailable = SystemInfo.isMac && Registry.is("ide.mac.touchbar.use", false);
    NSTLibrary lib = null;
    if (isTouchbarAvailable) {
        try {
        UrlClassLoader.loadPlatformLibrary("nst");

        // Set JNA to convert java.lang.String to char* using UTF-8, and match that with
        // the way we tell CF to interpret our char*
        // May be removed if we use toStringViaUTF16
        System.setProperty("jna.encoding", "UTF8");

        final Map<String, Object> nstOptions = new HashMap<>();
        lib = Native.loadLibrary("nst", NSTLibrary.class, nstOptions);
      } catch (Throwable e) {
        ourLog.error("Failed to load nst library for touchbar: ", e);
      }
    }
    ourNSTLibrary = lib;

    // TODO: must show only functional-keys when corresponding settings key is true
  }

  public static boolean isAvailable() { return ourNSTLibrary != null; }

  public static void initialize() {
    if (!isAvailable())
      return;

    final String[] testItems = new String[] {"Run"};
    final String[] testItemIds = new String[testItems.length];

    // fill available actions
    // register action on native side
    for (int c = 0; c < testItems.length; ++c) {
      String key = testItems[c];
      final TBItemCallback act = new TBItemCallback(key);
      ourActions.put(key, act);
      final String uid = "button."+key;
      testItemIds[c] = uid;
      ourNSTLibrary.registerItem(uid, "button", key, act);
    }

    final ID pool = Foundation.invoke("NSAutoreleasePool", "new");
    try {
      final ID app = Foundation.invoke("NSApplication", "sharedApplication");
      Foundation.invoke(app, "setAutomaticCustomizeTouchBarMenuItemEnabled:", true);

      final ID tb = Foundation.invoke(Foundation.invoke("NSTouchBar", "alloc"), "init");
      final ID tbd = Foundation.invoke(Foundation.invoke("NSTDelegate", "alloc"), "init");
      Foundation.invoke(tb, "setDelegate:", tbd);

      final Object[] nsTestItemIds = _str2ids(testItemIds);
      final ID items = Foundation.invoke("NSArray", "arrayWithObjects:", nsTestItemIds);

      Foundation.invoke(tb, "setDefaultItemIdentifiers:", items);

      // TODO: select best placement in responder-chain (probably need attach to main-window controller)
      Foundation.invoke(app, "setTouchBar:", tb);
    } finally {
      Foundation.invoke(pool, "release");
    }
  }

  private static @NotNull Object[] _str2ids(@NotNull String[] strs) {
    Object[] result = new Object[strs.length];
    for (int c = 0; c < strs.length; ++c)
      result[c] = Foundation.nsString(strs[c]);
    return result;
  }
}
