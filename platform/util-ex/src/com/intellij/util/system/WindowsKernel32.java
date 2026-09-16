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
import java.lang.invoke.VarHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * The {@code kernel32.dll} downcalls that more than one class needs. Windows only: the first call loads the library.
 * <p>
 * A {@code HANDLE} and every pointer travel as an address. A {@code BOOL} and a {@code DWORD} travel as an {@code int}.
 * A call that can fail takes a call-state segment of {@link #CALL_STATE}. The call writes its {@code GetLastError} code
 * there, so the code always belongs to that call. Read the code with {@link #lastError}.
 */
@ApiStatus.Internal
public final class WindowsKernel32 {
  private WindowsKernel32() { }

  /** {@code INVALID_HANDLE_VALUE}: the address that {@link #createFile} answers for a failure. */
  public static final long INVALID_HANDLE_VALUE = -1L;

  /** {@code FILE_READ_ATTRIBUTES} */
  public static final int FILE_READ_ATTRIBUTES = 0x80;
  /** {@code FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE} */
  public static final int FILE_SHARE_ALL = 0x1 | 0x2 | 0x4;
  /** {@code OPEN_EXISTING} */
  public static final int OPEN_EXISTING = 3;
  /** {@code FILE_FLAG_BACKUP_SEMANTICS}: a directory does not open without this flag. */
  public static final int FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;

  /** {@code PROCESS_QUERY_LIMITED_INFORMATION}: enough for a memory query since Windows 8.1. */
  public static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;

  /**
   * The layout of the state that a call captures. Allocate one segment of it per call sequence, and read it
   * with {@link #lastError}. The layout itself needs no library, so it is safe to touch on any operating system.
   */
  public static final StructLayout CALL_STATE = Linker.Option.captureStateLayout();

  /** @return the {@code GetLastError} code that the last call wrote into {@code callState} */
  @ApiStatus.Internal
  public static int lastError(@NotNull MemorySegment callState) {
    return (int)Handles.LAST_ERROR.get(callState, 0L);
  }

  /**
   * {@code HANDLE CreateFileW(LPCWSTR name, DWORD access, DWORD shareMode, LPSECURITY_ATTRIBUTES, DWORD disposition,
   * DWORD flags, HANDLE template)}, with no security attributes and no template file.
   *
   * @param name a UTF-16LE path that the caller allocated
   * @return the handle, or a segment of {@link #INVALID_HANDLE_VALUE}. Close the handle with {@link #closeHandle}.
   */
  @ApiStatus.Internal
  public static @NotNull MemorySegment createFile(
    @NotNull MemorySegment callState, @NotNull MemorySegment name, int access, int shareMode, int disposition, int flags
  ) {
    try {
      return (MemorySegment)Handles.CREATE_FILE.invokeExact(
        callState, name, access, shareMode, MemorySegment.NULL, disposition, flags, MemorySegment.NULL);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * {@code HANDLE OpenProcess(DWORD access, BOOL inheritHandle, DWORD pid)}, without handle inheritance.
   *
   * @return the handle, or {@link MemorySegment#NULL} for a failure. Close the handle with {@link #closeHandle}.
   */
  @ApiStatus.Internal
  public static @NotNull MemorySegment openProcess(@NotNull MemorySegment callState, int access, long pid) {
    try {
      return (MemorySegment)Handles.OPEN_PROCESS.invokeExact(callState, access, 0, (int)pid);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code BOOL CloseHandle(HANDLE)}. @return {@code true} when the call closed the handle */
  @ApiStatus.Internal
  public static boolean closeHandle(@NotNull MemorySegment handle) {
    try {
      return (int)Handles.CLOSE_HANDLE.invokeExact(handle) != 0;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** The library and the handles load on the first call, so a class of this package stays readable on another operating system. */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = WindowsSystemLibraries.lookup("kernel32.dll");
    private static final Linker.Option CAPTURE_LAST_ERROR = Linker.Option.captureCallState("GetLastError");

    static final VarHandle LAST_ERROR = CALL_STATE.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

    static final MethodHandle CREATE_FILE = LINKER.downcallHandle(
      KERNEL32.findOrThrow("CreateFileW"),
      FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS), CAPTURE_LAST_ERROR);

    static final MethodHandle OPEN_PROCESS = LINKER.downcallHandle(
      KERNEL32.findOrThrow("OpenProcess"), FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT), CAPTURE_LAST_ERROR);

    static final MethodHandle CLOSE_HANDLE = LINKER.downcallHandle(
      KERNEL32.findOrThrow("CloseHandle"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
  }
}
