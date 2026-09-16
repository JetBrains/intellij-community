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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * File system queries through {@code kernel32.dll} and {@code ntdll.dll} downcalls. Windows only: the first call loads the handles.
 */
@ApiStatus.Internal
public final class WindowsFileSystem {
  private static final Logger LOG = Logger.getInstance(WindowsFileSystem.class);

  private WindowsFileSystem() { }

  private static final int INVALID_FILE_ATTRIBUTES = -1;
  private static final int FILE_ATTRIBUTE_REPARSE_POINT = 0x400;

  private static final int FILE_CASE_SENSITIVE_INFORMATION = 71;  // FILE_INFORMATION_CLASS::FileCaseSensitiveInformation
  private static final int FILE_CS_FLAG_CASE_SENSITIVE_DIR = 1;
  private static final int FSCTL_QUERY_PERSISTENT_VOLUME_STATE = 0x9023C;
  private static final int PERSISTENT_VOLUME_STATE_DEV_VOLUME = 0x2000;
  private static final int PERSISTENT_VOLUME_STATE_TRUSTED_VOLUME = 0x4000;
  private static final int TRUSTED_DEV_VOLUME = PERSISTENT_VOLUME_STATE_DEV_VOLUME | PERSISTENT_VOLUME_STATE_TRUSTED_VOLUME;
  private static final StructLayout PERSISTENT_VOLUME_INFORMATION = MemoryLayout.structLayout(
    JAVA_INT.withName("VolumeFlags"), JAVA_INT.withName("FlagMask"), JAVA_INT.withName("Version"), JAVA_INT.withName("Reserved"));

  public static boolean isOnDevDrive(@NotNull Path path) {
    try (var arena = Arena.ofConfined()) {
      var callState = arena.allocate(WindowsKernel32.CALL_STATE);
      var name = arena.allocateFrom(path.toString(), StandardCharsets.UTF_16LE);
      var handle = WindowsKernel32.createFile(
        callState, name, WindowsKernel32.FILE_READ_ATTRIBUTES, 0x3, WindowsKernel32.OPEN_EXISTING, WindowsKernel32.FILE_FLAG_BACKUP_SEMANTICS);
      if (handle.address() == WindowsKernel32.INVALID_HANDLE_VALUE) {
        LOG.warn("CreateFile(" + path + "): " + WindowsKernel32.lastError(callState));
        return false;
      }
      try {
        var information = arena.allocate(PERSISTENT_VOLUME_INFORMATION);
        information.set(JAVA_INT, 4, TRUSTED_DEV_VOLUME);
        information.set(JAVA_INT, 8, 1);
        var returned = arena.allocate(JAVA_INT);
        var size = (int)information.byteSize();
        var success = (int)Handles.DEVICE_IO_CONTROL.invokeExact(
          callState, handle, FSCTL_QUERY_PERSISTENT_VOLUME_STATE, information, size, information, size, returned, MemorySegment.NULL);
        if (success == 0) {
          if (LOG.isDebugEnabled()) LOG.debug("DeviceIoControl(" + path + "): " + WindowsKernel32.lastError(callState));
          return false;
        }
        var flags = information.get(JAVA_INT, 0);
        if (LOG.isDebugEnabled()) LOG.debug(path + ": 0x" + Integer.toHexString(flags));
        return returned.get(JAVA_INT, 0) >= size && flags == TRUSTED_DEV_VOLUME;
      }
      finally {
        WindowsKernel32.closeHandle(handle);
      }
    }
    catch (Throwable failure) {
      throw new IllegalStateException(failure);
    }
  }

  /** @return {@code true} when {@code GetFileAttributesW} reports {@code FILE_ATTRIBUTE_REPARSE_POINT} for the path */
  public static boolean isReparsePoint(@NotNull Path path) {
    try (var arena = Arena.ofConfined()) {
      var name = arena.allocateFrom(path.toString(), StandardCharsets.UTF_16LE);
      var attributes = (int)Handles.GET_FILE_ATTRIBUTES.invokeExact(name);
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
    try (var arena = Arena.ofConfined()) {
      var name = arena.allocateFrom("\\\\?\\" + absolutePath, StandardCharsets.UTF_16LE);
      var callState = arena.allocate(WindowsKernel32.CALL_STATE);
      var handle = WindowsKernel32.createFile(
        callState, name, 0, WindowsKernel32.FILE_SHARE_ALL, WindowsKernel32.OPEN_EXISTING, WindowsKernel32.FILE_FLAG_BACKUP_SEMANTICS);
      if (handle.address() == WindowsKernel32.INVALID_HANDLE_VALUE) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("CreateFile(" + absolutePath + "): 0x" + Integer.toHexString(WindowsKernel32.lastError(callState)));
        }
        return FileAttributes.CaseSensitivity.UNKNOWN;
      }
      try {
        var ioStatusBlock = arena.allocate(WindowsNtDll.IO_STATUS_BLOCK);
        // FILE_CASE_SENSITIVE_INFORMATION { ULONG Flags; }, preset to a value the kernel never writes
        var information = arena.allocate(JAVA_INT);
        information.set(JAVA_INT, 0, -1);
        var status = WindowsNtDll.queryInformationFile(
          handle, ioStatusBlock, information, (int)information.byteSize(), FILE_CASE_SENSITIVE_INFORMATION);
        if (status != 0) {
          // https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-erref/596a1078-e883-4972-9bbc-49e60bebca55
          if (LOG.isDebugEnabled()) LOG.debug("NtQueryInformationFile(" + absolutePath + "): 0x" + Integer.toHexString(status));
          return FileAttributes.CaseSensitivity.UNKNOWN;
        }
        var flags = information.get(JAVA_INT, 0);
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
        WindowsKernel32.closeHandle(handle);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * The {@code kernel32.dll} downcalls that only this class needs. {@link WindowsKernel32} and {@link WindowsNtDll}
   * hold the shared ones. {@code HANDLE} is an address; {@code DWORD} and {@code BOOL} are {@code int}.
   */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = WindowsSystemLibraries.lookup("kernel32.dll");

    /** {@code DWORD GetFileAttributesW(LPCWSTR fileName)} */
    static final MethodHandle GET_FILE_ATTRIBUTES = LINKER.downcallHandle(
      KERNEL32.findOrThrow("GetFileAttributesW"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS));

    /**
     * {@code BOOL DeviceIoControl(HANDLE device, DWORD code, LPVOID inBuffer, DWORD inSize, LPVOID outBuffer, DWORD outSize,
     * LPDWORD returned, LPOVERLAPPED)}, with {@code GetLastError} captured into the leading call-state argument
     */
    static final MethodHandle DEVICE_IO_CONTROL = LINKER.downcallHandle(
      KERNEL32.findOrThrow("DeviceIoControl"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS),
      Linker.Option.captureCallState("GetLastError"));
  }
}
