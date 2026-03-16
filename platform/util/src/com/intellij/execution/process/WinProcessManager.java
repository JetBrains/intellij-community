// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.CurrentJavaVersion;
import com.intellij.util.ReflectionUtil;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/// Do not call this class directly - use [OSProcessUtil] or [Process] API instead.
@ApiStatus.Internal
public final class WinProcessManager {
  private static final Logger LOG = Logger.getInstance(WinProcessManager.class);

  private WinProcessManager() { }

  /// @deprecated use `Process#pid`
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static int getProcessId(Process process) {
    String processClassName = process.getClass().getName();
    if (processClassName.equals("java.lang.Win32Process") || processClassName.equals("java.lang.ProcessImpl")) {
      try {
        if (CurrentJavaVersion.currentJavaVersion().feature >= 9) {
          return ((Long)Process.class.getMethod("pid").invoke(process)).intValue();
        }
        else {
          long handle = Objects.requireNonNull(ReflectionUtil.getField(process.getClass(), process, long.class, "handle"));
          return Kernel32.INSTANCE.GetProcessId(new WinNT.HANDLE(Pointer.createConstant(handle)));
        }
      }
      catch (Throwable t) {
        throw new IllegalStateException("Failed to get PID from instance of " + process.getClass() + ", OS: " + SystemInfo.OS_NAME, t);
      }
    }

    throw new IllegalStateException("Unable to get PID from instance of " + process.getClass() + ", OS: " + SystemInfo.OS_NAME);
  }

  /// @deprecated use `ProcessHandle.current().pid()`
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static int getCurrentProcessId() {
    return Kernel32.INSTANCE.GetCurrentProcessId();
  }

  public static boolean kill(@NotNull Process process, boolean tree) {
    return kill(-1, process, tree);
  }

  public static boolean kill(int pid, boolean tree) {
    return kill(pid, null, tree);
  }

  private static boolean kill(int pid, @Nullable Process process, boolean tree) {
    LOG.assertTrue(pid > 0 || process != null);
    try {
      if (process != null) {
        pid = getProcessId(process);
      }
      String[] cmdArray = {"taskkill", "/f", "/pid", String.valueOf(pid), tree ? "/t" : ""};
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.join(" ", cmdArray));
      }
      Process p = new ProcessBuilder(cmdArray).redirectErrorStream(true).start();
      String output = StreamUtil.readText(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
      int res = p.waitFor();

      if (res != 0 && (process == null || process.isAlive())) {
        LOG.warn(String.join(" ", cmdArray) + " failed: " + output);
        return false;
      }
      else if (LOG.isDebugEnabled()) {
        LOG.debug(output);
      }

      return true;
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return false;
  }
}
