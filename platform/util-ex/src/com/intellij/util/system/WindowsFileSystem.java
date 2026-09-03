// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileAttributes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * File system queries through {@code kernel32.dll} and {@code ntdll.dll} downcalls. Windows only: the first call loads the handles.
 */
@ApiStatus.Internal
public final class WindowsFileSystem {
  private static final Logger LOG = Logger.getInstance(WindowsFileSystem.class);

  private WindowsFileSystem() { }

  private static final int INVALID_FILE_ATTRIBUTES = -1;
  private static final int FILE_ATTRIBUTE_REPARSE_POINT = 0x400;

  private static final long INVALID_HANDLE_VALUE = -1L;
  private static final int FILE_SHARE_ALL = 0x1 | 0x2 | 0x4;  // FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE
  private static final int OPEN_EXISTING = 3;
  private static final int FILE_FLAG_BACKUP_SEMANTICS = 0x02000000;

  private static final int FILE_CASE_SENSITIVE_INFORMATION = 71;  // FILE_INFORMATION_CLASS::FileCaseSensitiveInformation
  private static final int FILE_CS_FLAG_CASE_SENSITIVE_DIR = 1;

  /** @return {@code true} when {@code GetFileAttributesW} reports {@code FILE_ATTRIBUTE_REPARSE_POINT} for the path */
  public static boolean isReparsePoint(@NotNull Path path) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment name = arena.allocateFrom(path.toString(), StandardCharsets.UTF_16LE);
      int attributes = (int)Handles.GET_FILE_ATTRIBUTES.invokeExact(name);
      return attributes != INVALID_FILE_ATTRIBUTES && (attributes & FILE_ATTRIBUTE_REPARSE_POINT) != 0;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * Reads {@code FILE_CASE_SENSITIVE_INFORMATION} of a directory. NTFS on Windows 10 1803 and later supports it.
   *
   * @param absolutePath an absolute DOS path
   * @return the case sensitivity, or {@link FileAttributes.CaseSensitivity#UNKNOWN} when the directory cannot be opened or the query fails
   */
  public static FileAttributes.@NotNull CaseSensitivity caseSensitivity(@NotNull String absolutePath) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment name = arena.allocateFrom("\\\\?\\" + absolutePath, StandardCharsets.UTF_16LE);
      MemorySegment callState = arena.allocate(Handles.CALL_STATE_LAYOUT);
      MemorySegment handle = (MemorySegment)Handles.CREATE_FILE.invokeExact(
        callState, name, 0, FILE_SHARE_ALL, MemorySegment.NULL, OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS, MemorySegment.NULL);
      if (handle.address() == INVALID_HANDLE_VALUE) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("CreateFile(" + absolutePath + "): 0x" + Integer.toHexString((int)Handles.LAST_ERROR.get(callState, 0L)));
        }
        return FileAttributes.CaseSensitivity.UNKNOWN;
      }
      try {
        MemorySegment ioStatusBlock = arena.allocate(Handles.IO_STATUS_BLOCK);
        // FILE_CASE_SENSITIVE_INFORMATION { ULONG Flags; }, preset to a value the kernel never writes
        MemorySegment information = arena.allocate(JAVA_INT);
        information.set(JAVA_INT, 0, -1);
        int status = (int)Handles.NT_QUERY_INFORMATION_FILE.invokeExact(
          handle, ioStatusBlock, information, (int)information.byteSize(), FILE_CASE_SENSITIVE_INFORMATION);
        if (status != 0) {
          // https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-erref/596a1078-e883-4972-9bbc-49e60bebca55
          if (LOG.isDebugEnabled()) LOG.debug("NtQueryInformationFile(" + absolutePath + "): 0x" + Integer.toHexString(status));
          return FileAttributes.CaseSensitivity.UNKNOWN;
        }
        int flags = information.get(JAVA_INT, 0);
        if (flags == 0) {
          return FileAttributes.CaseSensitivity.INSENSITIVE;
        }
        if (flags == FILE_CS_FLAG_CASE_SENSITIVE_DIR) {
          return FileAttributes.CaseSensitivity.SENSITIVE;
        }
        LOG.warn("NtQueryInformationFile(" + absolutePath + "): unexpected 'FileCaseSensitiveInformation' value " + flags);
        return FileAttributes.CaseSensitivity.UNKNOWN;
      }
      finally {
        int ignored = (int)Handles.CLOSE_HANDLE.invokeExact(handle);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** Downcalls into {@code kernel32.dll} and {@code ntdll.dll}. {@code HANDLE} is an address; {@code DWORD}, {@code BOOL} and {@code NTSTATUS} are {@code int}. */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = WindowsSystemLibraries.lookup("kernel32.dll");
    private static final SymbolLookup NTDLL = WindowsSystemLibraries.lookup("ntdll.dll");

    /** {@code DWORD GetFileAttributesW(LPCWSTR fileName)} */
    static final MethodHandle GET_FILE_ATTRIBUTES = LINKER.downcallHandle(
      KERNEL32.findOrThrow("GetFileAttributesW"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS));

    private static final Linker.Option CAPTURE_LAST_ERROR = Linker.Option.captureCallState("GetLastError");
    static final StructLayout CALL_STATE_LAYOUT = Linker.Option.captureStateLayout();
    static final VarHandle LAST_ERROR = CALL_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

    /**
     * {@code HANDLE CreateFileW(LPCWSTR name, DWORD access, DWORD shareMode, LPSECURITY_ATTRIBUTES, DWORD disposition, DWORD flags, HANDLE template)},
     * with {@code GetLastError} captured into the leading call-state argument
     */
    static final MethodHandle CREATE_FILE = LINKER.downcallHandle(
      KERNEL32.findOrThrow("CreateFileW"),
      FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS),
      CAPTURE_LAST_ERROR);

    /** {@code BOOL CloseHandle(HANDLE)} */
    static final MethodHandle CLOSE_HANDLE = LINKER.downcallHandle(
      KERNEL32.findOrThrow("CloseHandle"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /** {@code IO_STATUS_BLOCK { union { NTSTATUS Status; PVOID Pointer; }; ULONG_PTR Information; }}, 16 bytes on x64 and ARM64 */
    static final StructLayout IO_STATUS_BLOCK = MemoryLayout.structLayout(ADDRESS.withName("Pointer"), JAVA_LONG.withName("Information"));

    /** {@code NTSTATUS NtQueryInformationFile(HANDLE file, PIO_STATUS_BLOCK, PVOID information, ULONG length, FILE_INFORMATION_CLASS class)} */
    static final MethodHandle NT_QUERY_INFORMATION_FILE = LINKER.downcallHandle(
      NTDLL.findOrThrow("NtQueryInformationFile"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));
  }
}
