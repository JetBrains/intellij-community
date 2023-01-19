// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jna;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.system.CpuArch;
import com.sun.jna.Native;
import org.jetbrains.annotations.NotNull;

public final class JnaLoader {
  private static Boolean ourJnaLoaded = null;

  public static synchronized void load(@NotNull Logger logger) {
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
        logger.warn("Unable to load JNA library (" +
                    "os=" + SystemInfoRt.OS_NAME + " " + SystemInfoRt.OS_VERSION +
                    ", jna.boot.library.path=" + System.getProperty("jna.boot.library.path") +
                    ")", t);
      }
    }
  }

  public static synchronized boolean isLoaded() {
    if (ourJnaLoaded == null) {
      load(Logger.getInstance(JnaLoader.class));
    }
    return ourJnaLoaded;
  }

  /**
   * {@code true}, if JNA's direct mapping feature ({@code Native.register}) is available.
   * If {@code false}, use JNA's standard library loading ({@code Native.load}) instead.
   * <p>
   * Direct mapping currently crashes JRE on function invocation on macOS arm64. Reproducible via JNA's {@code DirectCallbacksTest}.
   *
   * @see Native#register
   * @see Native#load
   */
  public static boolean isSupportsDirectMapping() {
    return !(SystemInfoRt.isMac && CpuArch.isArm64());
  }
}
