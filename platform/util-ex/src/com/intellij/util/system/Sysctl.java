// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Read access to the BSD {@code sysctl} tree through a {@code sysctlbyname} downcall. macOS only: the symbol is looked up on the first call.
 */
@ApiStatus.Internal
public final class Sysctl {
  private Sysctl() { }

  /**
   * Reads a string value, for example {@code hw.model}, with two calls: one for the size, one for the data.
   *
   * @return the value without its terminator, or {@code null} when the name is unknown or the value is empty
   */
  @LowLevelLocalMachineAccess
  public static @Nullable String stringByName(@NotNull String name) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment nameSegment = arena.allocateFrom(name);
      MemorySegment size = arena.allocate(JAVA_LONG);
      if (sysctlByName(nameSegment, MemorySegment.NULL, size) != 0) {
        return null;
      }
      long length = size.get(JAVA_LONG, 0);
      if (length <= 1) {
        return null;
      }
      MemorySegment value = arena.allocate(length);
      if (sysctlByName(nameSegment, value, size) != 0) {
        return null;
      }
      return value.getString(0);
    }
  }

  private static int sysctlByName(MemorySegment name, MemorySegment value, MemorySegment size) {
    try {
      return (int)Handles.SYSCTLBYNAME.invokeExact(name, value, size, MemorySegment.NULL, 0L);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();

    /** {@code int sysctlbyname(const char *name, void *oldp, size_t *oldlenp, void *newp, size_t newlen)}. {@code size_t} is 8 bytes on every supported macOS. */
    static final MethodHandle SYSCTLBYNAME;

    static {
      MemoryLayout sizeT = LINKER.canonicalLayouts().get("size_t");
      if (sizeT.byteSize() != JAVA_LONG.byteSize()) {
        throw new IllegalStateException("Unexpected size_t: " + sizeT);
      }
      SYSCTLBYNAME = LINKER.downcallHandle(
        LINKER.defaultLookup().findOrThrow("sysctlbyname"),
        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, sizeT));
    }
  }
}
