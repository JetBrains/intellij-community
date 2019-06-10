// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jna;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.sun.jna.Native;

public class JnaLoader {
  private static Boolean ourJnaLoaded = null;

  public static synchronized void load(Logger logger) {
    if (ourJnaLoaded == null) {
      ourJnaLoaded = Boolean.FALSE;
      try {
        long t = System.currentTimeMillis();
        int ptrSize = Native.POINTER_SIZE;
        t = System.currentTimeMillis() - t;
        logger.info("JNA library (" + (ptrSize << 3) + "-bit) loaded in " + t + " ms");
        ourJnaLoaded = Boolean.TRUE;
      }
      catch (Throwable t) {
        logger.warn("Unable to load JNA library (OS: " + SystemInfo.OS_NAME + " " + SystemInfo.OS_VERSION + ")", t);
      }
    }
  }

  public static synchronized boolean isLoaded() {
    if (ourJnaLoaded == null) {
      load(Logger.getInstance(JnaLoader.class));
    }
    return ourJnaLoaded;
  }
}