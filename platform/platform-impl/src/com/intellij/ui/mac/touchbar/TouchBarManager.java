// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.lang.UrlClassLoader;
import com.sun.jna.Native;

import java.util.HashMap;
import java.util.Map;

public class TouchBarManager {
  private static final Logger ourLog = Logger.getInstance(TouchBar.class);
  private static final NSTLibrary ourNSTLibrary;
  private static TouchBar ourTouchbar;

  static {
    // NOTE: can also check existence of process 'ControlStrip' to determine touchbar availability
    final boolean isSystemSupportTouchbar = SystemInfo.isMac && SystemInfo.isOsVersionAtLeast("10.12.1");
    NSTLibrary lib = null;
    if (isSystemSupportTouchbar && Registry.is("ide.mac.touchbar.use", false) && isTouchBarServerRunning()) {
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
  }

  public enum TOUCHBARS {
    main,
    debug,
    test; /*only for testing purposes*/

    private TouchBar myTB = null;

    TouchBar get() {
      if (myTB == null) {
        final ID pool = Foundation.invoke("NSAutoreleasePool", "new");
        try {
          if (this == main) {
            myTB = new TouchBar("main");
            myTB.addItem(new TBItemButtonText("Debug", new TBItemAction("Debug")));
            myTB.addItem(new TBItemButtonText("Run", new TBItemAction("Run")));
          }
          else if (this == debug) {
            myTB = new TouchBar("debug");
            myTB.addItem(new TBItemButtonText("Step", new TBItemAction("Step Over")));
          }
          else if (this == test) {
            myTB = new TouchBar("test");
            myTB.addItem(new TBItemButtonText("test1", TBItemCallback.createPrintTextCallback("pressed test1 button")));
            myTB.addItem(new TBItemButtonText("test2", TBItemCallback.createPrintTextCallback("pressed test2 button")));
          }
        }
        finally {
          Foundation.invoke(pool, "release");
        }

        if (myTB != null)
          myTB.flushItems();
      }
      return myTB;
    }

    void release() {
      if (myTB == null)
        return;

      myTB.release();
      myTB = null;
    }
  }


  public static void initialize() {
    if (!isTouchBarAvailable())
      return;

    final ID app = Foundation.invoke("NSApplication", "sharedApplication");
    Foundation.invoke(app, "setAutomaticCustomizeTouchBarMenuItemEnabled:", true);
  }

  public static void setCurrent(TOUCHBARS tbType) {
    if (tbType == null || !isTouchBarAvailable())
      return;

    ourTouchbar = tbType.get();
    if (ourTouchbar == null)
      return;

    final ID app = Foundation.invoke("NSApplication", "sharedApplication");
    Foundation.invoke(app, "setTouchBar:", ourTouchbar.getTbID());
  }

  static NSTLibrary getNSTLibrary() { return ourNSTLibrary; }

  private static boolean isTouchBarAvailable() { return ourNSTLibrary != null; }

  private static boolean isTouchBarServerRunning() {
    final GeneralCommandLine cmdLine = new GeneralCommandLine("pgrep", "TouchBarServer");
    try {
      final ProcessOutput out = ExecUtil.execAndGetOutput(cmdLine);
      return !out.getStdout().isEmpty();
    } catch (ExecutionException e) {
      ourLog.error(e);
    }
    return false;
  }
}
