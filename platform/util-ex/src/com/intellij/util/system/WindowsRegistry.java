// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Read access to the Windows registry through {@code advapi32.dll} downcalls. Windows only: the first call loads the DLL.
 * <p>
 * A missing key or value is a {@code null} result, not an error. Every other {@code LSTATUS} throws
 * {@link RegistryException} with the code. A {@code HKEY} travels as a {@code long}.
 */
@ApiStatus.Internal
public final class WindowsRegistry {
  private WindowsRegistry() { }

  /** A predefined root key. {@code winreg.h} defines it as {@code (HKEY)(ULONG_PTR)((LONG)value)}, so the value is sign-extended. */
  public enum Hive {
    CURRENT_USER(0x80000001),
    LOCAL_MACHINE(0x80000002);

    final long handle;

    Hive(int value) {
      handle = value;
    }
  }

  /** A registry call failed. {@link #errorCode} is the {@code LSTATUS} it returned. */
  public static final class RegistryException extends IOException {
    public final int errorCode;

    RegistryException(@NotNull String function, int errorCode) {
      super(function + " failed with Win32 error " + errorCode);
      this.errorCode = errorCode;
    }
  }

  private static final int ERROR_SUCCESS = 0;
  private static final int ERROR_FILE_NOT_FOUND = 2;
  private static final int ERROR_MORE_DATA = 234;

  private static final int RRF_RT_REG_SZ = 0x2;
  private static final int RRF_RT_REG_EXPAND_SZ = 0x4;
  private static final int RRF_RT_REG_DWORD = 0x10;
  private static final int RRF_NOEXPAND = 0x10000000;

  /**
   * @return a {@code REG_SZ} or {@code REG_EXPAND_SZ} value, not expanded, or {@code null} when the key or the value does not exist
   */
  public static @Nullable String getString(@NotNull Hive hive, @NotNull String key, @NotNull String value) throws RegistryException {
    byte[] data = getValue(hive, key, value, RRF_RT_REG_SZ | RRF_RT_REG_EXPAND_SZ | RRF_NOEXPAND);
    if (data == null) {
      return null;
    }
    String text = new String(data, StandardCharsets.UTF_16LE);
    int terminator = text.indexOf('\0');
    return terminator >= 0 ? text.substring(0, terminator) : text;
  }

  /**
   * @return a {@code REG_DWORD} value, or {@code null} when the key or the value does not exist
   */
  public static @Nullable Integer getInt(@NotNull Hive hive, @NotNull String key, @NotNull String value) throws RegistryException {
    byte[] data = getValue(hive, key, value, RRF_RT_REG_DWORD);
    if (data == null) {
      return null;
    }
    if (data.length != Integer.BYTES) {
      throw new RegistryException("RegGetValueW(" + value + ") returned " + data.length + " bytes for a DWORD", ERROR_MORE_DATA);
    }
    // A heap segment over a byte array has byte alignment, so the aligned JAVA_INT layout rejects it.
    return MemorySegment.ofArray(data).get(JAVA_INT_UNALIGNED, 0);
  }

  /** Reads the raw bytes of a value with {@code RegGetValueW}: one call for the size, one for the data. */
  private static byte @Nullable [] getValue(@NotNull Hive hive, @NotNull String key, @NotNull String value, int flags) throws RegistryException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment subKey = arena.allocateFrom(key, StandardCharsets.UTF_16LE);
      MemorySegment valueName = arena.allocateFrom(value, StandardCharsets.UTF_16LE);
      MemorySegment size = arena.allocate(JAVA_INT);
      int status = getValue(hive.handle, subKey, valueName, flags, MemorySegment.NULL, size);
      if (status == ERROR_FILE_NOT_FOUND) {
        return null;
      }
      if (status != ERROR_SUCCESS) {
        throw new RegistryException("RegGetValueW(" + key + '\\' + value + ")", status);
      }
      MemorySegment data = arena.allocate(size.get(JAVA_INT, 0));
      status = getValue(hive.handle, subKey, valueName, flags, data, size);
      if (status != ERROR_SUCCESS) {
        throw new RegistryException("RegGetValueW(" + key + '\\' + value + ")", status);
      }
      return data.asSlice(0, size.get(JAVA_INT, 0)).toArray(JAVA_BYTE);
    }
  }

  private static int getValue(long hive, MemorySegment subKey, MemorySegment value, int flags, MemorySegment data, MemorySegment size) {
    try {
      return (int)Handles.REG_GET_VALUE.invokeExact(hive, subKey, value, flags, MemorySegment.NULL, data, size);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * Downcalls into {@code advapi32.dll}, looked up by its absolute path. {@code HKEY} is {@code long},
   * {@code LSTATUS} and {@code DWORD} are {@code int}.
   */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup ADVAPI32 = SymbolLookup.libraryLookup(systemRoot().resolve("System32").resolve("advapi32.dll"), Arena.global());

    /** {@code LSTATUS RegGetValueW(HKEY, LPCWSTR subKey, LPCWSTR value, DWORD flags, LPDWORD type, PVOID data, LPDWORD size)} */
    static final MethodHandle REG_GET_VALUE = LINKER.downcallHandle(
      ADVAPI32.findOrThrow("RegGetValueW"),
      FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    private static @NotNull Path systemRoot() {
      String systemRoot = System.getenv("SystemRoot");
      return Path.of(systemRoot != null ? systemRoot : "C:\\Windows");
    }
  }
}
