// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.process.impl.ProcessListUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OSProcessUtil {
  private static final Logger LOG = Logger.getInstance(OSProcessUtil.class);
  private static String ourPid;

  public static ProcessInfo @NotNull [] getProcessList() {
    return ProcessListUtil.getProcessList();
  }

  public static boolean killProcessTree(@NotNull Process process) {
    if (SystemInfo.isWindows) {
      try {
        Integer pid = ProcessService.getInstance().winPtyChildProcessId(process);
        if (pid != null) {
          if (pid == -1) return true;
          boolean res = WinProcessManager.kill(pid, true);
          process.destroy();
          return res;
        }
        if (Registry.is("disable.winp", false)) {
          return WinProcessManager.kill(process, true);
        }
        else {
          if (!process.isAlive()) {
            logSkippedActionWithTerminatedProcess(process, "killProcessTree", null);
            return true;
          }
          ProcessService.getInstance().killWinProcessRecursively(process);
          return true;
        }
      }
      catch (Throwable e) {
        LOG.info("Cannot kill process tree", e);
      }
    }
    else if (SystemInfo.isUnix) {
      return UnixProcessManager.sendSigKillToProcessTree(process);
    }
    return false;
  }

  public static void killProcess(@NotNull Process process) {
    killProcess(getProcessID(process));
  }

  public static void killProcess(int pid) {
    if (SystemInfo.isWindows) {
      try {
        if (!Registry.is("disable.winp", false)) {
          try {
            ProcessService.getInstance().killWinProcess(pid);
            return;
          }
          catch (Throwable e) {
            LOG.error("Failed to kill process with winp, fallback to default logic", e);
          }
        }
        WinProcessManager.kill(pid, false);
      }
      catch (Throwable e) {
        LOG.info("Cannot kill process", e);
      }
    }
    else if (SystemInfo.isUnix) {
      UnixProcessManager.sendSignal(pid, UnixProcessManager.SIGKILL);
    }
  }
  /**
   * Terminates the process with the specified pid gracefully: on windows sends Ctrl-C,
   * on unix sends the SIGINT signal.
   *
   * @throws UnsupportedOperationException if cannot interrupt the process
   * @see KillableProcessHandler#destroyProcessGracefully()
   */
  public static void terminateProcessGracefully(@NotNull Process process) throws RuntimeException {
    if (SystemInfo.isWindows) {
      if (Registry.is("disable.winp")) {
        throw new UnsupportedOperationException("Cannot terminate process, disable.winp=true");
      }
      else {
        try {
          // there is no need to check return value: `sendCtrlC` either returns
          // true or throws exception.
          //noinspection ResultOfMethodCallIgnored
          ProcessService.getInstance().sendWinProcessCtrlC(process);
        }
        catch (Exception e) {
          throw new UnsupportedOperationException("Failed to terminate process", e);
        }
      }
    }
    else if (SystemInfo.isUnix) {
      UnixProcessManager.sendSignal((int)process.pid(), UnixProcessManager.SIGINT);
    }
    else {
      throw new UnsupportedOperationException("Graceful termination is not supported for " + SystemInfo.getOsNameAndVersion());
    }
  }

  /**
   * pid is not enough to emulate CTRL+C on Windows, we need a real process with stdin
   *
   * @deprecated use {@link #terminateProcessGracefully(Process)}
   */
  @Deprecated(forRemoval = true)
  public static void terminateProcessGracefully(int pid) throws RuntimeException {
    if (SystemInfo.isWindows) {
      if (Registry.is("disable.winp")) {
        throw new UnsupportedOperationException("Cannot terminate process, disable.winp=true");
      }
      else {
        try {
          // there is no need to check return value: `sendCtrlC` either returns
          // true or throws exception.
          //noinspection ResultOfMethodCallIgnored
          ProcessService.getInstance().sendWinProcessCtrlC(pid);
        }
        catch (Exception e) {
          throw new UnsupportedOperationException("Failed to terminate process", e);
        }
      }
    }
    else if (SystemInfo.isUnix) {
      UnixProcessManager.sendSignal(pid, UnixProcessManager.SIGINT);
    }
    else {
      throw new UnsupportedOperationException("Graceful termination is not supported for " + SystemInfo.getOsNameAndVersion());
    }
  }

  static void logSkippedActionWithTerminatedProcess(@NotNull Process process, @NotNull String actionName, @Nullable String commandLine) {
    Integer pid = null;
    try {
      pid = getProcessID(process);
    }
    catch (Throwable ignored) {
    }
    LOG.info("Cannot " + actionName + " already terminated process (pid: " + pid + ", command: " + commandLine + ")");
  }

  public static int getProcessID(@NotNull Process process) {
    return (int)process.pid();
  }

  /**
   * @deprecated use {@link #getProcessID(Process)}
   */
  @Deprecated(forRemoval = true)
  public static int getProcessID(@NotNull Process process, Boolean disableWinp) {
    return (int)process.pid();
  }

  public static int getCurrentProcessId() {
    return (int)ProcessHandle.current().pid();
  }

  public static String getApplicationPid() {
    if (ourPid == null) {
      ourPid = String.valueOf(getCurrentProcessId());
    }
    return ourPid;
  }
}
