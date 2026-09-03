// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * The {@link NativeAccess} provider on the FFM API. {@code META-INF/services} registers it, and {@code intellij.platform.util} finds it
 * through {@code ServiceLoader}. A probe that fails to bind on this system logs once and answers "unknown" from then on.
 * A file system probe that fails for one path logs and answers "unknown" for that path only.
 */
@ApiStatus.Internal
public final class NativeAccessImpl extends NativeAccess {
  private static final Logger LOG = Logger.getInstance(NativeAccessImpl.class);

  private volatile boolean windowsBuildNumberAvailable = true;
  private volatile boolean translatedProcessAvailable = true;
  private volatile boolean windowsNativeArchAvailable = true;
  private volatile boolean caseSensitivityAvailable = true;
  private volatile boolean reparsePointAvailable = true;

  @Override
  public @Nullable Long getWindowsBuildNumber() {
    if (OS.CURRENT != OS.Windows || !windowsBuildNumberAvailable) {
      return null;
    }
    try {
      // this key is undocumented but mentioned heavily all over the Internet
      String value = WindowsRegistry.getString(WindowsRegistry.Hive.LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "CurrentBuildNumber");
      return value != null ? Long.valueOf(value) : null;
    }
    catch (Throwable t) {
      windowsBuildNumberAvailable = false;
      LOG.warn("Unrecognized win version", t);
      return null;
    }
  }

  @Override
  public @Nullable Boolean isTranslatedProcess() {
    if (OS.CURRENT != OS.macOS || !translatedProcessAvailable) {
      return null;
    }
    try {
      // https://developer.apple.com/documentation/apple-silicon/about-the-rosetta-translation-environment
      // the key is absent on an Intel Mac
      Integer translated = Sysctl.intByName("sysctl.proc_translated");
      return translated != null && translated == 1;
    }
    catch (Throwable t) {
      translatedProcessAvailable = false;
      LOG.warn(t);
      return null;
    }
  }

  @Override
  public @Nullable CpuArch getWindowsNativeArch() {
    if (OS.CURRENT != OS.Windows || !windowsNativeArchAvailable) {
      return null;
    }
    try {
      return Wow64.nativeMachine();
    }
    catch (Throwable t) {
      windowsNativeArchAvailable = false;
      LOG.warn(t);
      return null;
    }
  }

  @Override
  public FileAttributes.@NotNull CaseSensitivity getDirectoryCaseSensitivity(@NotNull Path directory) {
    if (!caseSensitivityAvailable) {
      return FileAttributes.CaseSensitivity.UNKNOWN;
    }
    String path = directory.toAbsolutePath().toString();
    try {
      if (OS.CURRENT == OS.Windows) {
        // FILE_CASE_SENSITIVE_INFORMATION needs Windows 10, and the query opens the directory by a DOS path
        if (OS.CURRENT.isAtLeast(10, 0) && OSAgnosticPathUtil.isAbsoluteDosPath(path)) {
          return WindowsFileSystem.caseSensitivity(path);
        }
      }
      else if (OS.CURRENT == OS.macOS) {
        return MacFileSystem.caseSensitivity(path);
      }
      else if (OS.CURRENT == OS.Linux) {
        return LinuxFileSystem.caseSensitivity(path);
      }
    }
    catch (Throwable t) {
      caseSensitivityAvailable = !isBindFailure(t);
      LOG.warn("path: " + path, t);
    }
    return FileAttributes.CaseSensitivity.UNKNOWN;
  }

  @Override
  public @Nullable Boolean isReparsePoint(@NotNull Path path) {
    if (OS.CURRENT != OS.Windows || !reparsePointAvailable) {
      return null;
    }
    try {
      return WindowsFileSystem.isReparsePoint(path);
    }
    catch (Throwable t) {
      reparsePointAvailable = !isBindFailure(t);
      LOG.warn("path: " + path, t);
      return null;
    }
  }

  /**
   * A binding failure carries a {@link LinkageError} in its cause chain: the holder class of the downcall handles did not
   * initialize, or a later call found it in that state. Every other failure belongs to one path.
   */
  private static boolean isBindFailure(@NotNull Throwable t) {
    Throwable cause = t;
    for (int depth = 0; cause != null && depth < 10; depth++) {
      if (cause instanceof LinkageError) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  @Override
  public int kill(int pid, int signal) {
    if (OS.CURRENT == OS.Windows) {
      throw new IllegalStateException("kill is unavailable on Windows");
    }
    return PosixSignals.kill(pid, signal);
  }
}
