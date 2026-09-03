// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.util;

import com.intellij.util.system.WindowsSystemLibraries;
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

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/** The service control manager through {@code advapi32.dll} downcalls. Windows only: the first call loads the DLL. */
@ApiStatus.Internal
public final class WindowsServices {
  private WindowsServices() { }

  private static final int SC_MANAGER_CONNECT = 0x0001;
  /** {@code ERROR_SERVICE_DOES_NOT_EXIST} */
  public static final int ERROR_SERVICE_DOES_NOT_EXIST = 1060;

  /**
   * Opens the service with connect access and closes it again.
   *
   * @return 0 when the service exists, else the {@code GetLastError} code of {@code OpenServiceW}, for example {@link #ERROR_SERVICE_DOES_NOT_EXIST}
   * @throws IllegalStateException with the error code when the service control manager cannot be opened
   */
  public static int openService(@NotNull String serviceName) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment callState = arena.allocate(Handles.CALL_STATE_LAYOUT);
      MemorySegment manager = (MemorySegment)Handles.OPEN_SC_MANAGER.invokeExact(callState, MemorySegment.NULL, MemorySegment.NULL, SC_MANAGER_CONNECT);
      if (manager.address() == 0) {
        throw new IllegalStateException("OpenSCManagerW failed with Win32 error " + (int)Handles.LAST_ERROR.get(callState, 0L));
      }
      try {
        MemorySegment service = (MemorySegment)Handles.OPEN_SERVICE.invokeExact(callState, manager, arena.allocateFrom(serviceName, StandardCharsets.UTF_16LE), SC_MANAGER_CONNECT);
        if (service.address() == 0) {
          return (int)Handles.LAST_ERROR.get(callState, 0L);
        }
        int ignored = (int)Handles.CLOSE_SERVICE_HANDLE.invokeExact(service);
        return 0;
      }
      finally {
        int ignored = (int)Handles.CLOSE_SERVICE_HANDLE.invokeExact(manager);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup ADVAPI32 = WindowsSystemLibraries.lookup("advapi32.dll");
    private static final Linker.Option CAPTURE_LAST_ERROR = Linker.Option.captureCallState("GetLastError");

    static final StructLayout CALL_STATE_LAYOUT = Linker.Option.captureStateLayout();
    static final VarHandle LAST_ERROR = CALL_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

    /** {@code SC_HANDLE OpenSCManagerW(LPCWSTR machine, LPCWSTR database, DWORD access)} */
    static final MethodHandle OPEN_SC_MANAGER = LINKER.downcallHandle(
      ADVAPI32.findOrThrow("OpenSCManagerW"), FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT), CAPTURE_LAST_ERROR);
    /** {@code SC_HANDLE OpenServiceW(SC_HANDLE manager, LPCWSTR name, DWORD access)} */
    static final MethodHandle OPEN_SERVICE = LINKER.downcallHandle(
      ADVAPI32.findOrThrow("OpenServiceW"), FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT), CAPTURE_LAST_ERROR);
    /** {@code BOOL CloseServiceHandle(SC_HANDLE)} */
    static final MethodHandle CLOSE_SERVICE_HANDLE = LINKER.downcallHandle(ADVAPI32.findOrThrow("CloseServiceHandle"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
  }
}
