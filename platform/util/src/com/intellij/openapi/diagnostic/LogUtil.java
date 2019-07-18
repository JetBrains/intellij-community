// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class LogUtil {
  private LogUtil() { }

  public static String objectAndClass(@Nullable final Object o) {
    return o != null ? o + " (" + o.getClass().getName() + ")" : "null";
  }

  /**
   * Format string syntax as in {@linkplain String#format(String, Object...)}.
   */
  public static void debug(@NotNull Logger logger, @NonNls @NotNull String format, @Nullable Object... args) {
    if (logger.isDebugEnabled()) {
      logger.debug(String.format(format, args));
    }
  }

  public static String getProcessList() {
    try {
      @SuppressWarnings("SpellCheckingInspection") Process process = new ProcessBuilder()
        .command(SystemInfo.isWindows ? new String[]{System.getenv("windir") + "\\system32\\tasklist.exe", "/v"} : new String[]{"ps", "a"})
        .redirectErrorStream(true)
        .start();
      return FileUtil.loadTextAndClose(process.getInputStream());
    }
    catch (IOException e) {
      return ExceptionUtil.getThrowableText(e);
    }
  }

  public static String getSystemMemoryInfo() {
    try {
      @SuppressWarnings("SpellCheckingInspection") Process process = new ProcessBuilder()
        .command(SystemInfo.isWindows ? "systeminfo" : SystemInfo.isMac ? "vm_stat" : "free")
        .redirectErrorStream(true)
        .start();
      return FileUtil.loadTextAndClose(process.getInputStream());
    }
    catch (IOException e) {
      return ExceptionUtil.getThrowableText(e);
    }
  }
}
