// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * The {@code ntdll.dll} downcalls and the NT structure layouts. Windows only: the first call loads the library.
 * <p>
 * Each layout is for a 64-bit target, so a {@code ULONG_PTR} takes 8 bytes. A layout needs no library, so a test
 * can read it on any operating system. A downcall loads {@code ntdll.dll}.
 */
@ApiStatus.Internal
public final class WindowsNtDll {
  private WindowsNtDll() { }

  /**
   * {@code IO_STATUS_BLOCK}, 16 bytes: a union of {@code NTSTATUS Status} and {@code PVOID Pointer}, then
   * {@code ULONG_PTR Information}. The two arms of the union overlap at offset 0, so one pointer covers both.
   */
  public static final StructLayout IO_STATUS_BLOCK = MemoryLayout.structLayout(
    ADDRESS.withName("Pointer"),
    JAVA_LONG.withName("Information"));

  /**
   * {@code NTSTATUS NtQueryInformationFile(HANDLE file, PIO_STATUS_BLOCK, PVOID information, ULONG length,
   * FILE_INFORMATION_CLASS)}
   *
   * @return the {@code NTSTATUS}, where 0 reports success
   */
  @ApiStatus.Internal
  public static int queryInformationFile(
    @NotNull MemorySegment file, @NotNull MemorySegment statusBlock, @NotNull MemorySegment information, int length, int informationClass
  ) {
    try {
      return (int)Handles.QUERY_INFORMATION_FILE.invokeExact(file, statusBlock, information, length, informationClass);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** The library and the handles load on the first call, so the layouts above stay readable on another operating system. */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup NTDLL = WindowsSystemLibraries.lookup("ntdll.dll");

    static final MethodHandle QUERY_INFORMATION_FILE = LINKER.downcallHandle(
      NTDLL.findOrThrow("NtQueryInformationFile"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
  }
}
