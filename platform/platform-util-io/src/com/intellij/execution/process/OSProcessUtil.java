// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.execution.process.impl.ProcessListUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;

public final class OSProcessUtil {
  private static final Logger LOG = Logger.getInstance(OSProcessUtil.class);

  private OSProcessUtil() { }

  public static ProcessInfo @NotNull [] getProcessList() {
    return ProcessListUtil.getProcessList();
  }

  /// Forceful termination of a process tree.
  /// Returns `true` is the attempt was successful.
  public static boolean killProcessTree(@NotNull Process process) {
    if (OS.CURRENT == OS.Windows) {
      try {
        if (!Registry.is("disable.winp", false)) {
          try {
            LocalProcessService.getInstance().killWinProcessRecursively(process);
            return true;
          }
          catch (Throwable e) {
            LOG.error("Failed to kill " + process.pid() + " tree with WinP, falling back to the default logic", e);
          }
        }
        return WinProcessManager.kill(process, true);
      }
      catch (Throwable e) {
        LOG.info("Cannot kill process tree", e);
        return false;
      }
    }
    else {
      ProcessHandle handle;
      try {
        handle = process.toHandle();
      }
      catch (UnsupportedOperationException e) {
        // workaround for `com.pty4j.unix.UnixPtyProcess` (https://github.com/JetBrains/pty4j/issues/179)
        handle = ProcessHandle.of(process.pid()).orElse(null);
      }
      if (handle != null) {
        var handles = handle.descendants().toList();
        for (var iterator = handles.listIterator(handles.size()); iterator.hasPrevious(); ) {
          iterator.previous().destroyForcibly();
        }
      }
      process.destroyForcibly();
      return true;
    }
  }

  /// Prefer [Process#destroyForcibly()]
  @ApiStatus.Obsolete
  public static void killProcess(@NotNull Process process) {
    killProcess((int)process.pid());
  }

  /// Prefer [ProcessHandle#destroyForcibly()]
  @ApiStatus.Obsolete
  public static void killProcess(int pid) {
    if (OS.CURRENT == OS.Windows) {
      try {
        if (!Registry.is("disable.winp", false)) {
          try {
            LocalProcessService.getInstance().killWinProcess(pid);
            return;
          }
          catch (Throwable e) {
            LOG.error("Failed to kill " + pid + " with WinP, falling back to the default logic", e);
          }
        }
        WinProcessManager.kill(pid, false);
      }
      catch (Throwable e) {
        LOG.info("Cannot kill process", e);
      }
    }
    else {
      UnixProcessManager.sendSignal(pid, UnixProcessManager.SIGKILL);
    }
  }

  /// Terminates the specified process gracefully: on Windows, sends Ctrl-C, on Unix, sends the SIGINT signal.
  ///
  /// @throws UnsupportedOperationException if it cannot interrupt the process
  /// @see KillableProcessHandler#destroyProcessGracefully()
  public static void terminateProcessGracefully(@NotNull Process process) throws RuntimeException {
    terminateProcessGracefully((int)process.pid(), process.getOutputStream());
  }

  /// Terminates the specified process gracefully: on Windows, sends Ctrl-C, on Unix, sends the SIGINT signal.
  ///
  /// Just sending a CTRL+C event on Windows might not be enough to terminate the process (PY-50064).
  /// Use [#terminateProcessGracefully(Process)] or handle the case when the process doesn't terminate.
  ///
  /// @throws UnsupportedOperationException if it cannot interrupt the process
  /// @see KillableProcessHandler#destroyProcessGracefully()
  public static void terminateProcessGracefully(int pid) throws RuntimeException {
    terminateProcessGracefully(pid, null);
  }

  private static void terminateProcessGracefully(int pid, @Nullable OutputStream processOutputStream) throws RuntimeException {
    if (OS.CURRENT == OS.Windows) {
      if (Registry.is("disable.winp")) {
        throw new UnsupportedOperationException("Cannot terminate process, disable.winp=true");
      }
      else {
        try {
          // there is no need to check return value: `sendCtrlC` either returns `true` or throws an exception
          LocalProcessService.getInstance().sendWinProcessCtrlC(pid, processOutputStream);
        }
        catch (Exception e) {
          throw new UnsupportedOperationException("Failed to terminate process", e);
        }
      }
    }
    else {
      UnixProcessManager.sendSignal(pid, UnixProcessManager.SIGINT);
    }
  }

  /// @deprecated use [Process#pid()] directly
  @Deprecated(forRemoval = true)
  public static int getProcessID(@NotNull Process process) {
    return (int)process.pid();
  }

  /// @deprecated use `ProcessHandle.current().pid()`
  @Deprecated(forRemoval = true)
  public static int getCurrentProcessId() {
    return (int)ProcessHandle.current().pid();
  }

  /// @deprecated use `String.valueOf(ProcessHandle.current().pid())`
  @Deprecated(forRemoval = true)
  public static String getApplicationPid() {
    return String.valueOf(getCurrentProcessId());
  }
}
