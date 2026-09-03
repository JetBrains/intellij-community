// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * The architecture of the machine Windows runs on, from an {@code IsWow64Process2} downcall into {@code kernel32.dll}.
 * Windows only. The export exists since Windows 10 1709; on an older system the probe answers {@code null}.
 */
@ApiStatus.Internal
public final class Wow64 {
  private Wow64() { }

  private static final int IMAGE_FILE_MACHINE_I386 = 0x014C;
  private static final int IMAGE_FILE_MACHINE_AMD64 = 0x8664;
  private static final int IMAGE_FILE_MACHINE_ARM64 = 0xAA64;

  /**
   * @return the native machine architecture, or {@code null} when the call is unavailable, fails, or reports an unknown machine type
   */
  public static @Nullable CpuArch nativeMachine() {
    MethodHandle isWow64Process2 = Handles.IS_WOW64_PROCESS_2;
    if (isWow64Process2 == null) {
      return null;
    }
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment processMachine = arena.allocate(JAVA_SHORT);
      MemorySegment nativeMachine = arena.allocate(JAVA_SHORT);
      MemorySegment process = (MemorySegment)Handles.GET_CURRENT_PROCESS.invokeExact();
      int succeeded = (int)isWow64Process2.invokeExact(process, processMachine, nativeMachine);
      if (succeeded == 0) {
        return null;
      }
      return switch (Short.toUnsignedInt(nativeMachine.get(JAVA_SHORT, 0))) {
        case IMAGE_FILE_MACHINE_I386 -> CpuArch.X86;
        case IMAGE_FILE_MACHINE_AMD64 -> CpuArch.X86_64;
        case IMAGE_FILE_MACHINE_ARM64 -> CpuArch.ARM64;
        default -> null;
      };
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** Downcalls into {@code kernel32.dll}. {@code HANDLE} is an address, {@code BOOL} is {@code int}, {@code USHORT*} points at a {@code short}. */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = WindowsSystemLibraries.lookup("kernel32.dll");

    /** {@code HANDLE GetCurrentProcess()} */
    static final MethodHandle GET_CURRENT_PROCESS = LINKER.downcallHandle(
      KERNEL32.findOrThrow("GetCurrentProcess"),
      FunctionDescriptor.of(ADDRESS));

    /** {@code BOOL IsWow64Process2(HANDLE process, USHORT *processMachine, USHORT *nativeMachine)}, absent before Windows 10 1709 */
    static final @Nullable MethodHandle IS_WOW64_PROCESS_2 = KERNEL32.find("IsWow64Process2")
      .map(address -> LINKER.downcallHandle(address, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS)))
      .orElse(null);
  }
}
