// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.UUID;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/** The {@code ole32.dll} calls that a COM client needs. Windows only: the first call loads the library. */
@ApiStatus.Internal
public final class WindowsCom {
  private static final int COINIT_APARTMENTTHREADED = 2;

  /** {@code RPC_E_CHANGED_MODE}: the thread already holds an apartment of another kind. */
  public static final int RPC_E_CHANGED_MODE = 0x80010106;

  /** {@code CLSCTX_INPROC_SERVER}: the object runs in this process. */
  public static final int CLSCTX_INPROC_SERVER = 0x1;

  /** {@code CLSCTX_INPROC_SERVER | CLSCTX_INPROC_HANDLER}: the object runs in this process, with a handler. */
  public static final int CLSCTX_INPROC = 0x1 | 0x2;

  private WindowsCom() { }

  /** Throws when {@code hresult} reports a failure. */
  @ApiStatus.Internal
  public static void checkResult(@NotNull String function, int hresult) throws WindowsComException {
    if (hresult < 0) throw new WindowsComException(function, hresult);
  }

  /**
   * {@code CoInitializeEx(NULL, COINIT_APARTMENTTHREADED)}. The caller ends the apartment with
   * {@link #uninitializeApartment}, or keeps it for the life of the process.
   *
   * @return the {@code HRESULT}. {@link #RPC_E_CHANGED_MODE} reports an apartment of another kind, which is not a failure.
   */
  @ApiStatus.Internal
  public static int initializeApartment() {
    return Downcalls.callInt(() -> (int)Handles.INITIALIZE.invokeExact(MemorySegment.NULL, COINIT_APARTMENTTHREADED));
  }

  /** {@code CoUninitialize()}: ends one apartment of {@link #initializeApartment}. */
  @ApiStatus.Internal
  public static void uninitializeApartment() {
    Downcalls.run(() -> {
      Handles.UNINITIALIZE.invokeExact();
    });
  }

  /** Runs {@code action} in an apartment, and ends the apartment afterwards. */
  static <T> T withApartment(@NotNull Operation<T> action) throws Throwable {
    var result = initializeApartment();
    if (result != RPC_E_CHANGED_MODE) checkResult("CoInitializeEx", result);
    try {
      return action.run();
    }
    finally {
      if (result >= 0) uninitializeApartment();
    }
  }

  /**
   * {@code CoCreateInstance(classId, NULL, context, interfaceId, &result)}
   *
   * @param result a segment of one address, which receives the interface pointer
   * @return the {@code HRESULT}
   */
  @ApiStatus.Internal
  public static int createInstance(
    @NotNull MemorySegment classId, int context, @NotNull MemorySegment interfaceId, @NotNull MemorySegment result
  ) {
    return Downcalls.callInt(
      () -> (int)Handles.CREATE_INSTANCE.invokeExact(classId, MemorySegment.NULL, context, interfaceId, result));
  }

  /**
   * A {@code GUID} in memory: {@code Data1}, {@code Data2} and {@code Data3} in native order, then the eight
   * {@code Data4} bytes as written. This replaces a {@code CLSIDFromString} call on a text form of the same value.
   */
  @ApiStatus.Internal
  public static @NotNull MemorySegment guid(@NotNull Arena arena, @NotNull UUID id) {
    var guid = arena.allocate(16, 4);
    var high = id.getMostSignificantBits();
    guid.set(JAVA_INT, 0, (int)(high >>> 32));
    guid.set(JAVA_SHORT, 4, (short)(high >>> 16));
    guid.set(JAVA_SHORT, 6, (short)high);
    var low = id.getLeastSignificantBits();
    for (var index = 0; index < 8; index++) {
      guid.set(JAVA_BYTE, 8L + index, (byte)(low >>> (56 - 8 * index)));
    }
    return guid;
  }

  /**
   * The address of the method at {@code index} of the virtual table of {@code instance}.
   * Index 2 is {@code IUnknown::Release}.
   */
  @ApiStatus.Internal
  public static @NotNull MemorySegment method(@NotNull MemorySegment instance, int index) {
    var table = instance.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
    return table.reinterpret((index + 1) * ADDRESS.byteSize()).getAtIndex(ADDRESS, index);
  }

  @FunctionalInterface
  interface Operation<T> {
    T run() throws Throwable;
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup OLE32 = WindowsSystemLibraries.ole32();
    static final MethodHandle INITIALIZE =
      LINKER.downcallHandle(OLE32.findOrThrow("CoInitializeEx"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    static final MethodHandle UNINITIALIZE = LINKER.downcallHandle(OLE32.findOrThrow("CoUninitialize"), FunctionDescriptor.ofVoid());
    static final MethodHandle CREATE_INSTANCE = LINKER.downcallHandle(
      OLE32.findOrThrow("CoCreateInstance"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
  }
}
