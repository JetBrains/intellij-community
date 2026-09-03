// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.util.system.WindowsSystemLibraries;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.function.LongPredicate;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Downcalls into {@code user32.dll}. Windows only: the first call loads the DLL.
 * A window or icon handle travels as a {@code long}; {@code 0} is a null handle.
 */
@ApiStatus.Internal
public final class User32Ex {
  private User32Ex() { }

  /** {@code GW_OWNER} for {@link #getWindow} */
  public static final int GW_OWNER = 4;
  /** {@code SPI_GETSCREENREADER} */
  public static final int SPI_GETSCREENREADER = 0x0046;
  /** {@code SPI_GETFOREGROUNDLOCKTIMEOUT} */
  public static final int SPI_GETFOREGROUNDLOCKTIMEOUT = 0x2000;
  /** {@code SPI_SETFOREGROUNDLOCKTIMEOUT} */
  public static final int SPI_SETFOREGROUNDLOCKTIMEOUT = 0x2001;

  /** @return the offset of the best icon in an {@code .ico} image, or 0 when there is none */
  public static int lookupIconIdFromDirectoryEx(@NotNull MemorySegment resourceBits, boolean icon, int width, int height, int flags) {
    try {
      return (int)Handles.LOOKUP_ICON_ID_FROM_DIRECTORY_EX.invokeExact(resourceBits, icon ? 1 : 0, width, height, flags);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** @return an {@code HICON} the caller owns, or 0 on failure. Release it with {@link #destroyIcon}. */
  public static long createIconFromResourceEx(@NotNull MemorySegment resourceBits, int resourceSize, boolean icon, int version, int width, int height, int flags) {
    try {
      MemorySegment handle = (MemorySegment)Handles.CREATE_ICON_FROM_RESOURCE_EX.invokeExact(resourceBits, resourceSize, icon ? 1 : 0, version, width, height, flags);
      return handle.address();
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  public static boolean destroyIcon(long icon) {
    return call(Handles.DESTROY_ICON, icon);
  }

  public static void flashWindow(long window, boolean invert) {
    try {
      int ignored = (int)Handles.FLASH_WINDOW.invokeExact(MemorySegment.ofAddress(window), invert ? 1 : 0);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** @return a {@code BOOL} system parameter, or {@code null} when the call fails */
  public static @Nullable Boolean systemParametersInfoBool(int action) {
    Integer value = systemParametersInfoUInt(action);
    return value != null ? value != 0 : null;
  }

  /** @return a {@code UINT} system parameter, or {@code null} when the call fails */
  public static @Nullable Integer systemParametersInfoUInt(int action) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment value = arena.allocate(JAVA_INT);
      int succeeded = (int)Handles.SYSTEM_PARAMETERS_INFO.invokeExact(action, 0, value, 0);
      return succeeded != 0 ? value.get(JAVA_INT, 0) : null;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** Sets a system parameter whose {@code pvParam} carries the value itself, for example {@link #SPI_SETFOREGROUNDLOCKTIMEOUT}. */
  public static boolean systemParametersInfoSetUInt(int action, int value) {
    try {
      return (int)Handles.SYSTEM_PARAMETERS_INFO.invokeExact(action, 0, MemorySegment.ofAddress(Integer.toUnsignedLong(value)), 0) != 0;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** Lets the process call {@code SetForegroundWindow}; a {@code DWORD} process id, so the upper half of a {@code long} pid is dropped. */
  public static void allowSetForegroundWindow(long processId) {
    try {
      int ignored = (int)Handles.ALLOW_SET_FOREGROUND_WINDOW.invokeExact((int)processId);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  public static boolean setForegroundWindow(long window) {
    return call(Handles.SET_FOREGROUND_WINDOW, window);
  }

  public static boolean isWindowVisible(long window) {
    return call(Handles.IS_WINDOW_VISIBLE, window);
  }

  /** @return the related window, for example the owner for {@link #GW_OWNER}, or 0 */
  public static long getWindow(long window, int command) {
    try {
      MemorySegment result = (MemorySegment)Handles.GET_WINDOW.invokeExact(MemorySegment.ofAddress(window), command);
      return result.address();
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** @return the id of the process that created the window, or 0 when the call fails */
  public static int getWindowProcessId(long window) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment processId = arena.allocate(JAVA_INT);
      int threadId = (int)Handles.GET_WINDOW_THREAD_PROCESS_ID.invokeExact(MemorySegment.ofAddress(window), processId);
      return threadId != 0 ? processId.get(JAVA_INT, 0) : 0;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** @return the title bar text, or an empty string when the window has none */
  public static @NotNull String getWindowText(long window) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment handle = MemorySegment.ofAddress(window);
      int length = (int)Handles.GET_WINDOW_TEXT_LENGTH.invokeExact(handle);
      if (length <= 0) {
        return "";
      }
      int capacity = length + 1;
      MemorySegment buffer = arena.allocate(2L * capacity);
      int copied = (int)Handles.GET_WINDOW_TEXT.invokeExact(handle, buffer, capacity);
      return copied > 0 ? new String(buffer.asSlice(0, 2L * copied).toArray(JAVA_BYTE), StandardCharsets.UTF_16LE) : "";
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /**
   * Calls {@code visitor} for every top-level window until it returns {@code false}.
   * An exception from the visitor stops the enumeration and is rethrown after the downcall returns.
   */
  public static void enumWindows(@NotNull LongPredicate visitor) {
    EnumWindowsVisitor state = new EnumWindowsVisitor(visitor);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment callback = Handles.LINKER.upcallStub(Handles.ENUM_WINDOWS_CALLBACK.bindTo(state), Handles.WNDENUMPROC, arena);
      int ignored = (int)Handles.ENUM_WINDOWS.invokeExact(callback, 0L);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
    if (state.failure instanceof Error error) {
      throw error;
    }
    if (state.failure != null) {
      throw (RuntimeException)state.failure;
    }
  }

  /** An upcall must not throw, so the visitor's exception or error is stored and the enumeration stops. */
  private static final class EnumWindowsVisitor {
    private final LongPredicate visitor;
    /** A {@link RuntimeException} or an {@link Error}; {@link LongPredicate#test} declares nothing else. */
    Throwable failure;

    EnumWindowsVisitor(LongPredicate visitor) {
      this.visitor = visitor;
    }

    @SuppressWarnings("unused")
    int visit(MemorySegment window, long lParam) {
      try {
        return visitor.test(window.address()) ? 1 : 0;
      }
      catch (Throwable t) {
        failure = t;
        return 0;
      }
    }
  }

  private static boolean call(MethodHandle handle, long window) {
    try {
      return (int)handle.invokeExact(MemorySegment.ofAddress(window)) != 0;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code HWND}, {@code HICON} and {@code PBYTE} are addresses; {@code BOOL}, {@code DWORD}, {@code UINT} and {@code int} are {@code int}; {@code LPARAM} is {@code long}. */
  private static final class Handles {
    static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup USER32 = WindowsSystemLibraries.lookup("user32.dll");

    /** {@code int LookupIconIdFromDirectoryEx(PBYTE, BOOL icon, int cx, int cy, UINT flags)} */
    static final MethodHandle LOOKUP_ICON_ID_FROM_DIRECTORY_EX = downcall("LookupIconIdFromDirectoryEx", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
    /** {@code HICON CreateIconFromResourceEx(PBYTE, DWORD size, BOOL icon, DWORD version, int cx, int cy, UINT flags)} */
    static final MethodHandle CREATE_ICON_FROM_RESOURCE_EX = downcall("CreateIconFromResourceEx", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
    /** {@code BOOL DestroyIcon(HICON)} */
    static final MethodHandle DESTROY_ICON = downcall("DestroyIcon", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    /** {@code BOOL FlashWindow(HWND, BOOL invert)} */
    static final MethodHandle FLASH_WINDOW = downcall("FlashWindow", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    /** {@code BOOL SystemParametersInfoW(UINT action, UINT param, PVOID value, UINT winIni)} */
    static final MethodHandle SYSTEM_PARAMETERS_INFO = downcall("SystemParametersInfoW", FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));
    /** {@code BOOL AllowSetForegroundWindow(DWORD processId)} */
    static final MethodHandle ALLOW_SET_FOREGROUND_WINDOW = downcall("AllowSetForegroundWindow", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
    /** {@code BOOL SetForegroundWindow(HWND)} */
    static final MethodHandle SET_FOREGROUND_WINDOW = downcall("SetForegroundWindow", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    /** {@code BOOL IsWindowVisible(HWND)} */
    static final MethodHandle IS_WINDOW_VISIBLE = downcall("IsWindowVisible", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    /** {@code HWND GetWindow(HWND, UINT command)} */
    static final MethodHandle GET_WINDOW = downcall("GetWindow", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
    /** {@code DWORD GetWindowThreadProcessId(HWND, LPDWORD processId)} */
    static final MethodHandle GET_WINDOW_THREAD_PROCESS_ID = downcall("GetWindowThreadProcessId", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    /** {@code int GetWindowTextLengthW(HWND)} */
    static final MethodHandle GET_WINDOW_TEXT_LENGTH = downcall("GetWindowTextLengthW", FunctionDescriptor.of(JAVA_INT, ADDRESS));
    /** {@code int GetWindowTextW(HWND, LPWSTR buffer, int capacity)} */
    static final MethodHandle GET_WINDOW_TEXT = downcall("GetWindowTextW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
    /** {@code BOOL EnumWindows(WNDENUMPROC, LPARAM)} */
    static final MethodHandle ENUM_WINDOWS = downcall("EnumWindows", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG));
    /** {@code BOOL CALLBACK WNDENUMPROC(HWND, LPARAM)} */
    static final FunctionDescriptor WNDENUMPROC = FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG);
    static final MethodHandle ENUM_WINDOWS_CALLBACK;

    static {
      try {
        ENUM_WINDOWS_CALLBACK = MethodHandles.lookup().findVirtual(EnumWindowsVisitor.class, "visit", MethodType.methodType(int.class, MemorySegment.class, long.class));
      }
      catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
      return LINKER.downcallHandle(USER32.findOrThrow(name), descriptor);
    }
  }
}
