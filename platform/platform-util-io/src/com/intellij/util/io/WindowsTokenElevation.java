// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.util.system.WindowsSystemLibraries;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.lang.foreign.Arena;
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
 * Whether the access token of this process is elevated, from {@code OpenProcessToken} and {@code GetTokenInformation} downcalls
 * into {@code advapi32.dll}. Windows only: the first call loads the DLLs.
 */
@ApiStatus.Internal
public final class WindowsTokenElevation {
  private WindowsTokenElevation() { }

  private static final int TOKEN_QUERY = 0x0008;
  /** {@code TOKEN_INFORMATION_CLASS::TokenElevation} */
  private static final int TOKEN_ELEVATION_CLASS = 20;

  /**
   * @return {@code TOKEN_ELEVATION.TokenIsElevated != 0}
   * @throws IOException with the {@code GetLastError} code when a call fails
   */
  public static boolean isElevated() throws IOException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment callState = arena.allocate(Handles.CALL_STATE_LAYOUT);
      MemorySegment process = (MemorySegment)Handles.GET_CURRENT_PROCESS.invokeExact();
      MemorySegment tokenHandle = arena.allocate(ADDRESS);
      if ((int)Handles.OPEN_PROCESS_TOKEN.invokeExact(callState, process, TOKEN_QUERY, tokenHandle) == 0) {
        throw new IOException("OpenProcessToken: " + (int)Handles.LAST_ERROR.get(callState, 0L));
      }
      MemorySegment token = tokenHandle.get(ADDRESS, 0);
      try {
        // TOKEN_ELEVATION { DWORD TokenIsElevated; }
        MemorySegment elevation = arena.allocate(JAVA_INT);
        MemorySegment returnLength = arena.allocate(JAVA_INT);
        if ((int)Handles.GET_TOKEN_INFORMATION.invokeExact(callState, token, TOKEN_ELEVATION_CLASS, elevation, (int)elevation.byteSize(), returnLength) == 0) {
          throw new IOException("GetTokenInformation: " + (int)Handles.LAST_ERROR.get(callState, 0L));
        }
        return elevation.get(JAVA_INT, 0) != 0;
      }
      finally {
        int ignored = (int)Handles.CLOSE_HANDLE.invokeExact(token);
      }
    }
    catch (IOException e) {
      throw e;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code HANDLE} is an address; {@code BOOL} and {@code DWORD} are {@code int}. The two calls that can fail capture {@code GetLastError}. */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = WindowsSystemLibraries.lookup("kernel32.dll");
    private static final SymbolLookup ADVAPI32 = WindowsSystemLibraries.lookup("advapi32.dll");
    private static final Linker.Option CAPTURE_LAST_ERROR = Linker.Option.captureCallState("GetLastError");

    static final StructLayout CALL_STATE_LAYOUT = Linker.Option.captureStateLayout();
    static final VarHandle LAST_ERROR = CALL_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

    /** {@code HANDLE GetCurrentProcess()} */
    static final MethodHandle GET_CURRENT_PROCESS = LINKER.downcallHandle(KERNEL32.findOrThrow("GetCurrentProcess"), FunctionDescriptor.of(ADDRESS));
    /** {@code BOOL CloseHandle(HANDLE)} */
    static final MethodHandle CLOSE_HANDLE = LINKER.downcallHandle(KERNEL32.findOrThrow("CloseHandle"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
    /** {@code BOOL OpenProcessToken(HANDLE process, DWORD access, PHANDLE token)} */
    static final MethodHandle OPEN_PROCESS_TOKEN = LINKER.downcallHandle(
      ADVAPI32.findOrThrow("OpenProcessToken"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS), CAPTURE_LAST_ERROR);
    /** {@code BOOL GetTokenInformation(HANDLE token, TOKEN_INFORMATION_CLASS, LPVOID info, DWORD length, PDWORD returnLength)} */
    static final MethodHandle GET_TOKEN_INFORMATION = LINKER.downcallHandle(
      ADVAPI32.findOrThrow("GetTokenInformation"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS), CAPTURE_LAST_ERROR);
  }
}
