package com.intellij.util.system;

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

final class WindowsCom {
  private static final int COINIT_APARTMENTTHREADED = 2;
  private static final int RPC_E_CHANGED_MODE = 0x80010106;

  private WindowsCom() { }

  static void checkResult(@NotNull String function, int hresult) throws WindowsComException {
    if (hresult < 0) throw new WindowsComException(function, hresult);
  }

  static <T> T withApartment(@NotNull Operation<T> action) throws Throwable {
    var result = (int)Handles.INITIALIZE.invokeExact(MemorySegment.NULL, COINIT_APARTMENTTHREADED);
    if (result != RPC_E_CHANGED_MODE) checkResult("CoInitializeEx", result);
    try {
      return action.run();
    }
    finally {
      if (result >= 0) Handles.UNINITIALIZE.invokeExact();
    }
  }

  static @NotNull MemorySegment guid(@NotNull Arena arena, @NotNull UUID id) {
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

  @FunctionalInterface
  interface Operation<T> {
    T run() throws Throwable;
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup OLE32 = WindowsSystemLibraries.lookup("ole32.dll");
    static final MethodHandle INITIALIZE =
      LINKER.downcallHandle(OLE32.findOrThrow("CoInitializeEx"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    static final MethodHandle UNINITIALIZE = LINKER.downcallHandle(OLE32.findOrThrow("CoUninitialize"), FunctionDescriptor.ofVoid());
  }
}
