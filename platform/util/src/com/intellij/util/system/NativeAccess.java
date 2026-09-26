// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Native calls that {@code intellij.platform.util} needs but cannot make itself.
 * The module compiles for Java 8, and the FFM API needs Java 22.
 * {@code intellij.platform.util.ex} registers the implementation through {@link ServiceLoader}.
 * Without a provider, for example in the JPS build process, every probe answers "unknown" and the caller falls back to pure Java.
 */
@ApiStatus.Internal
public abstract class NativeAccess {
  private static final class Holder {
    private static final NativeAccess INSTANCE = load();

    private static NativeAccess load() {
      try {
        Iterator<NativeAccess> providers = ServiceLoader.load(NativeAccess.class, NativeAccess.class.getClassLoader()).iterator();
        if (providers.hasNext()) {
          return providers.next();
        }
      }
      catch (Throwable t) {
        Logger.getInstance(NativeAccess.class).warn("Native access provider is unavailable", t);
      }
      return new NativeAccess() { };
    }
  }

  public static @NotNull NativeAccess getInstance() {
    return Holder.INSTANCE;
  }

  /** @return the Windows build number, or {@code null} when it is unknown */
  public @Nullable Long getWindowsBuildNumber() {
    return null;
  }

  /** @return {@code true} when Rosetta 2 translates this process, or {@code null} when it is unknown */
  public @Nullable Boolean isTranslatedProcess() {
    return null;
  }

  /**
   * @return the architecture of the Windows machine this process runs on, or {@code null} when it is unknown.
   * Under WoW64 it differs from {@link CpuArch#CURRENT}.
   */
  public @Nullable CpuArch getWindowsNativeArch() {
    return null;
  }

  /** @return the case sensitivity of {@code directory} from a file system query, or {@link FileAttributes.CaseSensitivity#UNKNOWN} */
  public @NotNull FileAttributes.CaseSensitivity getDirectoryCaseSensitivity(@NotNull Path directory) {
    return FileAttributes.CaseSensitivity.UNKNOWN;
  }

  /** @return {@code true} when {@code path} is an NTFS reparse point, or {@code null} when it is unknown */
  public @Nullable Boolean isReparsePoint(@NotNull Path path) {
    return null;
  }

  /**
   * Sends {@code signal} to the process {@code pid}, or to the process group {@code -pid}.
   *
   * @return the {@code kill(2)} result: 0 on success, -1 on failure
   * @throws IllegalStateException when no provider is registered
   */
  public int kill(int pid, int signal) {
    throw new IllegalStateException("No native access provider, OS: " + OS.CURRENT);
  }
}
