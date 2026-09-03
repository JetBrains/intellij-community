// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import com.intellij.openapi.util.io.FileAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * File system queries through libc downcalls. macOS only: the symbol is looked up on the first call.
 */
@ApiStatus.Internal
public final class MacFileSystem {
  private MacFileSystem() { }

  /** {@code _PC_CASE_SENSITIVE} in {@code unistd.h}: 1 when the volume compares names case-sensitively, else 0 */
  private static final int PC_CASE_SENSITIVE = 11;

  /**
   * Asks {@code pathconf} whether the volume that holds {@code path} is case-sensitive.
   *
   * @return the case sensitivity, or {@link FileAttributes.CaseSensitivity#UNKNOWN} when the path does not exist or the query fails
   */
  public static FileAttributes.@NotNull CaseSensitivity caseSensitivity(@NotNull String path) {
    try (Arena arena = Arena.ofConfined()) {
      long result = (long)Handles.PATHCONF.invokeExact(arena.allocateFrom(path), PC_CASE_SENSITIVE);
      if (result == 1) {
        return FileAttributes.CaseSensitivity.SENSITIVE;
      }
      if (result == 0) {
        return FileAttributes.CaseSensitivity.INSENSITIVE;
      }
      return FileAttributes.CaseSensitivity.UNKNOWN;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();

    /** {@code long pathconf(const char *path, int name)} */
    static final MethodHandle PATHCONF = LINKER.downcallHandle(
      LINKER.defaultLookup().findOrThrow("pathconf"),
      FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT));
  }
}
