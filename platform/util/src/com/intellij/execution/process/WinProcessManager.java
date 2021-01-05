// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.lang.JavaVersion;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import org.jetbrains.annotations.NonNls;

import java.util.Objects;

/**
 * Do not call this class directly - use {@link OSProcessUtil} instead.
 */
public final class WinProcessManager {
  private static final Logger LOG = Logger.getInstance(WinProcessManager.class);

  private WinProcessManager() { }

  @ReviseWhenPortedToJDK("9")
  public static int getProcessId(Process process) {
    String processClassName = process.getClass().getName();
    if (processClassName.equals("java.lang.Win32Process") || processClassName.equals("java.lang.ProcessImpl")) {
      try {
        if (JavaVersion.current().feature >= 9) {
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

  public static int getCurrentProcessId() {
    return Kernel32.INSTANCE.GetCurrentProcessId();
  }

  public static boolean kill(Process process, boolean tree) {
    return kill(-1, process, tree);
  }

  public static boolean kill(int pid, boolean tree) {
    return kill(pid, null, tree);
  }

  private static boolean kill(int pid, Process process, boolean tree) {
    LOG.assertTrue(pid > 0 || process != null);
    try {
      if (process != null) {
        pid = getProcessId(process);
      }
      @NonNls String[] cmdArray = {"taskkill", "/f", "/pid", String.valueOf(pid), tree ? "/t" : ""};
      if (LOG.isDebugEnabled()) {
        LOG.debug(StringUtil.join(cmdArray, " "));
      }
      Process p = new ProcessBuilder(cmdArray).redirectErrorStream(true).start();
      String output = FileUtil.loadTextAndClose(p.getInputStream());
      int res = p.waitFor();

      if (res != 0 && (process == null || isAlive(process))) {
        LOG.warn(StringUtil.join(cmdArray, " ") + " failed: " + output);
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

  private static boolean isAlive(Process process) {
    try {
      process.exitValue();
      return false;
    } catch(IllegalThreadStateException e) {
      return true;
    }
  }
}
