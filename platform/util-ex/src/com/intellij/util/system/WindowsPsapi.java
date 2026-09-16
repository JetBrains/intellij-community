// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/** The {@code psapi.dll} downcalls that more than one class needs. Windows only: the first call loads the library. */
@ApiStatus.Internal
public final class WindowsPsapi {
  private WindowsPsapi() { }

  /**
   * {@code BOOL GetProcessMemoryInfo(HANDLE process, PPROCESS_MEMORY_COUNTERS counters, DWORD size)}
   * <p>
   * The caller owns the layout of {@code counters}, because the call answers a longer structure for a larger
   * {@code size}. Set the {@code cb} field to the size before the call.
   *
   * @return {@code true} when the call filled {@code counters}
   */
  @ApiStatus.Internal
  public static boolean processMemoryInfo(@NotNull MemorySegment process, @NotNull MemorySegment counters) {
    return Downcalls.callInt(
      () -> (int)Handles.GET_PROCESS_MEMORY_INFO.invokeExact(process, counters, (int)counters.byteSize())) != 0;
  }

  private static final class Handles {
    static final MethodHandle GET_PROCESS_MEMORY_INFO = Linker.nativeLinker().downcallHandle(
      WindowsSystemLibraries.psapi().findOrThrow("GetProcessMemoryInfo"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
  }
}
