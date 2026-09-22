// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import com.intellij.util.ArrayUtilRt;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;

/**
 * Read access to the Windows registry through {@code advapi32.dll} downcalls. Windows only: the first call loads the DLL.
 * <p>
 * A missing key or value is a {@code null} result, not an error. Every other {@code LSTATUS} throws
 * {@link RegistryException} with the code. A {@code HKEY} travels as a {@code long}.
 * <p>
 * A read through a {@link View} opens the key with {@code RegOpenKeyExW} and the matching {@code KEY_WOW64_*} access right,
 * so a 32-bit process can read the 64-bit view and the other way round.
 * <p>
 * The static methods open and close a key per call. A {@link Key} keeps a key open for several reads, and it is the only
 * way to read a hive file that is not mounted, through {@link Key#loadAppKey}.
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

  /** The registry view to read; {@code winnt.h} defines the {@code KEY_WOW64_*} access rights. */
  public enum View {
    /** The view of this process. */
    DEFAULT(0),
    /** {@code KEY_WOW64_64KEY} */
    WOW64_64(0x0100),
    /** {@code KEY_WOW64_32KEY} */
    WOW64_32(0x0200);

    final int accessRight;

    View(int accessRight) {
      this.accessRight = accessRight;
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
  private static final int ERROR_NO_MORE_ITEMS = 259;

  private static final int KEY_READ = 0x20019;

  private static final int REG_SZ = 1;
  private static final int REG_EXPAND_SZ = 2;
  private static final int REG_DWORD = 4;
  private static final int REG_MULTI_SZ = 7;
  private static final int REG_QWORD = 11;

  private static final int RRF_RT_REG_SZ = 0x2;
  private static final int RRF_RT_REG_EXPAND_SZ = 0x4;
  private static final int RRF_RT_REG_DWORD = 0x10;
  private static final int RRF_NOEXPAND = 0x10000000;

  /**
   * @return a {@code REG_SZ} or {@code REG_EXPAND_SZ} value, not expanded, or {@code null} when the key or the value does not exist
   */
  public static @Nullable String getString(@NotNull Hive hive, @NotNull String key, @NotNull String value) throws RegistryException {
    return toString(getValue(hive, key, value, RRF_RT_REG_SZ | RRF_RT_REG_EXPAND_SZ | RRF_NOEXPAND));
  }

  /**
   * @return a {@code REG_SZ} or {@code REG_EXPAND_SZ} value from the given view, not expanded, or {@code null} when the key or the value does not exist
   */
  public static @Nullable String getString(@NotNull Hive hive, @NotNull String key, @NotNull String value, @NotNull View view) throws RegistryException {
    if (view == View.DEFAULT) {
      return getString(hive, key, value);
    }
    try (var arena = Arena.ofConfined()) {
      var openedKey = arena.allocate(JAVA_LONG);
      var status = openKey(hive.handle, arena.allocateFrom(key, StandardCharsets.UTF_16LE), KEY_READ | view.accessRight, openedKey);
      if (status == ERROR_FILE_NOT_FOUND) {
        return null;
      }
      if (status != ERROR_SUCCESS) {
        throw new RegistryException("RegOpenKeyExW(" + key + ")", status);
      }
      var handle = openedKey.get(JAVA_LONG, 0);
      try {
        return toString(getValue(handle, MemorySegment.NULL, arena.allocateFrom(value, StandardCharsets.UTF_16LE), RRF_RT_REG_SZ | RRF_RT_REG_EXPAND_SZ | RRF_NOEXPAND, arena, key + '\\' + value));
      }
      finally {
        var _ = closeKey(handle);
      }
    }
  }

  /**
   * @return the names of the direct subkeys, or {@code null} when the key does not exist
   */
  public static @NotNull String @Nullable [] subKeys(@NotNull Hive hive, @NotNull String key) throws RegistryException {
    try (var opened = Key.open(hive, key)) {
      return opened == null ? null : opened.subKeys();
    }
  }

  /**
   * An open registry key. Close it after use; the handle is not released before that.
   * <p>
   * A path is relative to the key and uses a backslash as the separator. A missing subkey or value is a {@code null}
   * result, not an error. The handle is safe to share between threads, because each call allocates its own memory.
   */
  public static final class Key implements AutoCloseable {
    private final long handle;
    private final String path;
    private volatile boolean closed;

    private Key(long handle, @NotNull String path) {
      this.handle = handle;
      this.path = path;
    }

    /**
     * Opens a key for read access with {@code RegOpenKeyExW}. An empty path opens the hive itself.
     *
     * @return the key, or {@code null} when it does not exist
     */
    public static @Nullable Key open(@NotNull Hive hive, @NotNull String path) throws RegistryException {
      return open(hive.handle, path, path);
    }

    /**
     * Loads a hive file, such as {@code privateregistry.bin}, with {@code RegLoadAppKeyW} and opens its root for read access.
     * The file stays locked until the key is closed. A file that another process holds open fails with
     * {@code ERROR_SHARING_VIOLATION}, and a missing file fails with {@code ERROR_FILE_NOT_FOUND}.
     */
    public static @NotNull Key loadAppKey(@NotNull Path hiveFile) throws RegistryException {
      var file = hiveFile.toAbsolutePath().toString();
      try (var arena = Arena.ofConfined()) {
        var result = arena.allocate(JAVA_LONG);
        int status;
        try {
          status = (int)Handles.REG_LOAD_APP_KEY.invokeExact(arena.allocateFrom(file, StandardCharsets.UTF_16LE), result, KEY_READ, 0, 0);
        }
        catch (Throwable t) {
          throw new IllegalStateException(t);
        }
        if (status != ERROR_SUCCESS) {
          throw new RegistryException("RegLoadAppKeyW(" + file + ")", status);
        }
        return new Key(result.get(JAVA_LONG, 0), file);
      }
    }

    private static @Nullable Key open(long parent, @NotNull String subKey, @NotNull String path) throws RegistryException {
      try (var arena = Arena.ofConfined()) {
        var result = arena.allocate(JAVA_LONG);
        var status = openKey(parent, arena.allocateFrom(subKey, StandardCharsets.UTF_16LE), KEY_READ, result);
        if (status == ERROR_FILE_NOT_FOUND) {
          return null;
        }
        if (status != ERROR_SUCCESS) {
          throw new RegistryException("RegOpenKeyExW(" + path + ")", status);
        }
        return new Key(result.get(JAVA_LONG, 0), path);
      }
    }

    /**
     * Opens a subkey for read access.
     *
     * @return the subkey, or {@code null} when it does not exist
     */
    public @Nullable Key open(@NotNull String subKey) throws RegistryException {
      return open(handle(), subKey, child(subKey));
    }

    /** @return {@code true} when the subkey exists and this process can open it for read access */
    public boolean exists(@NotNull String subKey) throws RegistryException {
      try (var opened = open(subKey)) {
        return opened != null;
      }
    }

    /**
     * Lists the direct subkeys with {@code RegQueryInfoKeyW} and {@code RegEnumKeyExW}.
     *
     * @return the names of the direct subkeys, in registry order
     */
    public @NotNull String @NotNull [] subKeys() throws RegistryException {
      var key = handle();
      try (var arena = Arena.ofConfined()) {
        var count = arena.allocate(JAVA_INT);
        var maxNameLength = arena.allocate(JAVA_INT);
        queryInfo(key, count, maxNameLength, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
        var capacity = maxNameLength.get(JAVA_INT, 0) + 1;
        var name = arena.allocate(2L * capacity);
        var nameLength = arena.allocate(JAVA_INT);
        var result = new String[count.get(JAVA_INT, 0)];
        for (int i = 0; i < result.length; i++) {
          nameLength.set(JAVA_INT, 0, capacity);
          int status;
          try {
            status = (int)Handles.REG_ENUM_KEY_EX.invokeExact(key, i, name, nameLength, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
          }
          catch (Throwable t) {
            throw new IllegalStateException(t);
          }
          if (status == ERROR_NO_MORE_ITEMS) {
            return Arrays.copyOf(result, i);
          }
          if (status != ERROR_SUCCESS) {
            throw new RegistryException("RegEnumKeyExW(" + path + ", " + i + ")", status);
          }
          result[i] = utf16(name, nameLength.get(JAVA_INT, 0));
        }
        return result;
      }
    }

    /**
     * Reads every value of the key with {@code RegQueryInfoKeyW} and {@code RegEnumValueW}.
     * <p>
     * {@code REG_SZ} and {@code REG_EXPAND_SZ} map to a {@link String}, not expanded. {@code REG_MULTI_SZ} maps to a
     * {@code String[]}. {@code REG_DWORD} maps to an {@link Integer}, {@code REG_QWORD} to a {@link Long}.
     * Every other type, such as {@code REG_BINARY}, maps to the raw {@code byte[]}.
     *
     * @return the values by name, in registry order; the default value has the empty name
     */
    public @NotNull Map<String, Object> values() throws RegistryException {
      var key = handle();
      try (var arena = Arena.ofConfined()) {
        var count = arena.allocate(JAVA_INT);
        var maxNameLength = arena.allocate(JAVA_INT);
        var maxDataLength = arena.allocate(JAVA_INT);
        queryInfo(key, MemorySegment.NULL, MemorySegment.NULL, count, maxNameLength, maxDataLength);
        var nameCapacity = maxNameLength.get(JAVA_INT, 0) + 1;
        var name = arena.allocate(2L * nameCapacity);
        var nameLength = arena.allocate(JAVA_INT);
        var type = arena.allocate(JAVA_INT);
        var data = arena.allocate(Math.max(maxDataLength.get(JAVA_INT, 0), Long.BYTES));
        var dataLength = arena.allocate(JAVA_INT);
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0, n = count.get(JAVA_INT, 0); i < n; i++) {
          int status;
          while (true) {
            nameLength.set(JAVA_INT, 0, nameCapacity);
            dataLength.set(JAVA_INT, 0, (int)data.byteSize());
            try {
              status = (int)Handles.REG_ENUM_VALUE.invokeExact(key, i, name, nameLength, MemorySegment.NULL, type, data, dataLength);
            }
            catch (Throwable t) {
              throw new IllegalStateException(t);
            }
            // Another process can write a longer value after the query. The call reports the size it needs.
            if (status != ERROR_MORE_DATA || dataLength.get(JAVA_INT, 0) <= data.byteSize()) {
              break;
            }
            data = arena.allocate(dataLength.get(JAVA_INT, 0));
          }
          if (status == ERROR_NO_MORE_ITEMS) {
            break;
          }
          if (status != ERROR_SUCCESS) {
            throw new RegistryException("RegEnumValueW(" + path + ", " + i + ")", status);
          }
          var bytes = data.asSlice(0, dataLength.get(JAVA_INT, 0)).toArray(JAVA_BYTE);
          result.put(utf16(name, nameLength.get(JAVA_INT, 0)), convert(type.get(JAVA_INT, 0), bytes));
        }
        return result;
      }
    }

    /**
     * Reads one value with {@code RegGetValueW}.
     *
     * @return a {@code REG_SZ} or {@code REG_EXPAND_SZ} value, not expanded, or {@code null} when the value does not exist
     */
    public @Nullable String getString(@NotNull String value) throws RegistryException {
      var key = handle();
      try (var arena = Arena.ofConfined()) {
        var valueName = arena.allocateFrom(value, StandardCharsets.UTF_16LE);
        return WindowsRegistry.toString(getValue(key, MemorySegment.NULL, valueName, RRF_RT_REG_SZ | RRF_RT_REG_EXPAND_SZ | RRF_NOEXPAND, arena, child(value)));
      }
    }

    /** Releases the handle with {@code RegCloseKey}. A second call does nothing. */
    @Override
    public void close() {
      if (!closed) {
        closed = true;
        var _ = closeKey(handle);
      }
    }

    @Override
    public String toString() {
      return "Key(" + path + (closed ? ", closed)" : ")");
    }

    private long handle() {
      if (closed) {
        throw new IllegalStateException("The key is closed: " + path);
      }
      return handle;
    }

    private @NotNull String child(@NotNull String name) {
      return path.isEmpty() ? name : path + '\\' + name;
    }

    private void queryInfo(long key, MemorySegment subKeys, MemorySegment maxSubKeyLength, MemorySegment values, MemorySegment maxValueNameLength, MemorySegment maxValueLength) throws RegistryException {
      int status;
      try {
        status = (int)Handles.REG_QUERY_INFO_KEY.invokeExact(key, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL, subKeys, maxSubKeyLength,
                                                             MemorySegment.NULL, values, maxValueNameLength, maxValueLength, MemorySegment.NULL, MemorySegment.NULL);
      }
      catch (Throwable t) {
        throw new IllegalStateException(t);
      }
      if (status != ERROR_SUCCESS) {
        throw new RegistryException("RegQueryInfoKeyW(" + path + ")", status);
      }
    }

    private static @NotNull String utf16(@NotNull MemorySegment buffer, int length) {
      return new String(buffer.asSlice(0, 2L * length).toArray(JAVA_BYTE), StandardCharsets.UTF_16LE);
    }

    private static @NotNull Object convert(int type, byte @NotNull [] data) {
      switch (type) {
        case REG_SZ, REG_EXPAND_SZ -> {
          return decode(data);
        }
        case REG_MULTI_SZ -> {
          var text = new String(data, StandardCharsets.UTF_16LE);
          var end = text.indexOf("\0\0");
          var body = end >= 0 ? text.substring(0, end) : text;
          return body.isEmpty() ? ArrayUtilRt.EMPTY_STRING_ARRAY : body.split("\0");
        }
        case REG_DWORD -> {
          if (data.length == Integer.BYTES) {
            return MemorySegment.ofArray(data).get(JAVA_INT_UNALIGNED, 0);
          }
          return data;
        }
        case REG_QWORD -> {
          if (data.length == Long.BYTES) {
            return MemorySegment.ofArray(data).get(JAVA_LONG_UNALIGNED, 0);
          }
          return data;
        }
        default -> {
          return data;
        }
      }
    }
  }

  private static @Nullable String toString(byte @Nullable [] data) {
    return data == null ? null : decode(data);
  }

  /** Decodes a {@code REG_SZ} buffer; the text ends at the first terminator, or at the end of the buffer without one. */
  private static @NotNull String decode(byte @NotNull [] data) {
    var text = new String(data, StandardCharsets.UTF_16LE);
    var terminator = text.indexOf('\0');
    return terminator >= 0 ? text.substring(0, terminator) : text;
  }

  /**
   * @return a {@code REG_DWORD} value, or {@code null} when the key or the value does not exist
   */
  public static @Nullable Integer getInt(@NotNull Hive hive, @NotNull String key, @NotNull String value) throws RegistryException {
    var data = getValue(hive, key, value, RRF_RT_REG_DWORD);
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
    try (var arena = Arena.ofConfined()) {
      var subKey = arena.allocateFrom(key, StandardCharsets.UTF_16LE);
      var valueName = arena.allocateFrom(value, StandardCharsets.UTF_16LE);
      return getValue(hive.handle, subKey, valueName, flags, arena, key + '\\' + value);
    }
  }

  private static byte @Nullable [] getValue(long key, MemorySegment subKey, MemorySegment valueName, int flags, Arena arena, String path) throws RegistryException {
    var size = arena.allocate(JAVA_INT);
    var status = getValue(key, subKey, valueName, flags, MemorySegment.NULL, size);
    if (status == ERROR_FILE_NOT_FOUND) {
      return null;
    }
    if (status != ERROR_SUCCESS) {
      throw new RegistryException("RegGetValueW(" + path + ")", status);
    }
    var data = arena.allocate(size.get(JAVA_INT, 0));
    status = getValue(key, subKey, valueName, flags, data, size);
    if (status != ERROR_SUCCESS) {
      throw new RegistryException("RegGetValueW(" + path + ")", status);
    }
    return data.asSlice(0, size.get(JAVA_INT, 0)).toArray(JAVA_BYTE);
  }

  private static int openKey(long hive, MemorySegment subKey, int access, MemorySegment result) {
    try {
      return (int)Handles.REG_OPEN_KEY_EX.invokeExact(hive, subKey, 0, access, result);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static int closeKey(long key) {
    try {
      return (int)Handles.REG_CLOSE_KEY.invokeExact(key);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
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
   * Downcalls into {@code advapi32.dll}. {@code HKEY} is {@code long},
   * {@code LSTATUS} and {@code DWORD} are {@code int}.
   */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup ADVAPI32 = WindowsSystemLibraries.advapi32();

    /** {@code LSTATUS RegGetValueW(HKEY, LPCWSTR subKey, LPCWSTR value, DWORD flags, LPDWORD type, PVOID data, LPDWORD size)} */
    static final MethodHandle REG_GET_VALUE = LINKER.downcallHandle(
      ADVAPI32.findOrThrow("RegGetValueW"),
      FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

    /** {@code LSTATUS RegOpenKeyExW(HKEY, LPCWSTR subKey, DWORD options, REGSAM access, PHKEY result)} */
    static final MethodHandle REG_OPEN_KEY_EX = LINKER.downcallHandle(
      ADVAPI32.findOrThrow("RegOpenKeyExW"),
      FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));

    /** {@code LSTATUS RegQueryInfoKeyW(HKEY, LPWSTR class, LPDWORD classLength, LPDWORD reserved, LPDWORD subKeys, LPDWORD maxSubKeyLength, LPDWORD maxClassLength, LPDWORD values, LPDWORD maxValueNameLength, LPDWORD maxValueLength, LPDWORD securityDescriptor, PFILETIME lastWriteTime)} */
    static final MethodHandle REG_QUERY_INFO_KEY = LINKER.downcallHandle(
      ADVAPI32.findOrThrow("RegQueryInfoKeyW"),
      FunctionDescriptor.of(JAVA_INT, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    /** {@code LSTATUS RegEnumKeyExW(HKEY, DWORD index, LPWSTR name, LPDWORD nameLength, LPDWORD reserved, LPWSTR class, LPDWORD classLength, PFILETIME lastWriteTime)} */
    static final MethodHandle REG_ENUM_KEY_EX = LINKER.downcallHandle(
      ADVAPI32.findOrThrow("RegEnumKeyExW"),
      FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    /** {@code LSTATUS RegEnumValueW(HKEY, DWORD index, LPWSTR name, LPDWORD nameLength, LPDWORD reserved, LPDWORD type, LPBYTE data, LPDWORD dataLength)} */
    static final MethodHandle REG_ENUM_VALUE = LINKER.downcallHandle(
      ADVAPI32.findOrThrow("RegEnumValueW"),
      FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    /** {@code LSTATUS RegLoadAppKeyW(LPCWSTR file, PHKEY result, REGSAM access, DWORD options, DWORD reserved)} */
    static final MethodHandle REG_LOAD_APP_KEY = LINKER.downcallHandle(
      ADVAPI32.findOrThrow("RegLoadAppKeyW"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

    /** {@code LSTATUS RegCloseKey(HKEY)} */
    static final MethodHandle REG_CLOSE_KEY = LINKER.downcallHandle(ADVAPI32.findOrThrow("RegCloseKey"), FunctionDescriptor.of(JAVA_INT, JAVA_LONG));
  }
}
