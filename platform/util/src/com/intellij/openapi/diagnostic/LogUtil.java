/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        .command(new String[]{SystemInfo.isWindows ? "systeminfo" : SystemInfo.isMac ? "vm_stat" : "free"})
        .redirectErrorStream(true)
        .start();
      return FileUtil.loadTextAndClose(process.getInputStream());
    }
    catch (IOException e) {
      return ExceptionUtil.getThrowableText(e);
    }
  }
}
