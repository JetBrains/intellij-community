// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.util;

import com.intellij.util.system.WindowsSystemLibraries;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Process creation and control through {@code kernel32.dll} downcalls. Windows only: the first call loads the DLL.
 * A process handle travels as a {@code long}.
 */
@ApiStatus.Internal
public final class WindowsProcesses {
  private WindowsProcesses() { }

  /** {@code INFINITE} */
  public static final int INFINITE = -1;
  /** {@code WAIT_TIMEOUT} */
  public static final int WAIT_TIMEOUT = 0x102;
  /** {@code STILL_ACTIVE}, the exit code of a running process */
  public static final int STILL_ACTIVE = 259;
  /** {@code SW_NORMAL} */
  public static final int SW_NORMAL = 1;

  private static final int STARTF_USESHOWWINDOW = 0x1;
  private static final int CREATE_UNICODE_ENVIRONMENT = 0x400;
  private static final int FORMAT_MESSAGE_ALLOCATE_BUFFER = 0x100;
  private static final int FORMAT_MESSAGE_IGNORE_INSERTS = 0x200;
  private static final int FORMAT_MESSAGE_FROM_SYSTEM = 0x1000;
  /** {@code MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT)} */
  private static final int LANG_USER_DEFAULT = 0x400;

  /**
   * {@code CreateProcessW} without handle inheritance; the primary thread handle is closed at once.
   *
   * @param environmentBlock {@code NAME=value} entries, each ended by {@code \0}, and a final {@code \0}; {@code null} inherits the environment
   * @return the process handle; close it with {@link #closeHandle}
   * @throws IOException with the {@code GetLastError} code and its system message when the call fails
   */
  public static long createProcess(@NotNull String commandLine, @Nullable String environmentBlock, @NotNull String workingDirectory, int showWindow) throws IOException {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment startupInfo = arena.allocate(Handles.STARTUPINFOW);
      startupInfo.set(JAVA_INT, 0, (int)Handles.STARTUPINFOW.byteSize());
      startupInfo.set(JAVA_INT, Handles.STARTUPINFOW.byteOffset(MemoryLayout.PathElement.groupElement("dwFlags")), STARTF_USESHOWWINDOW);
      startupInfo.set(JAVA_SHORT, Handles.STARTUPINFOW.byteOffset(MemoryLayout.PathElement.groupElement("wShowWindow")), (short)showWindow);
      MemorySegment processInformation = arena.allocate(Handles.PROCESS_INFORMATION);
      MemorySegment environment = environmentBlock != null ? arena.allocateFrom(environmentBlock, StandardCharsets.UTF_16LE) : MemorySegment.NULL;
      MemorySegment callState = arena.allocate(Handles.CALL_STATE_LAYOUT);
      int succeeded = (int)Handles.CREATE_PROCESS.invokeExact(
        callState, MemorySegment.NULL, arena.allocateFrom(commandLine, StandardCharsets.UTF_16LE), MemorySegment.NULL, MemorySegment.NULL, 0,
        CREATE_UNICODE_ENVIRONMENT, environment, arena.allocateFrom(workingDirectory, StandardCharsets.UTF_16LE), startupInfo, processInformation);
      if (succeeded == 0) {
        int error = (int)Handles.LAST_ERROR.get(callState, 0L);
        throw new IOException("error " + error + ": " + formatMessage(error));
      }
      MemorySegment thread = processInformation.get(ADDRESS, Handles.PROCESS_INFORMATION.byteOffset(MemoryLayout.PathElement.groupElement("hThread")));
      if (thread.address() != 0) {
        int ignored = (int)Handles.CLOSE_HANDLE.invokeExact(thread);
      }
      return processInformation.get(ADDRESS, Handles.PROCESS_INFORMATION.byteOffset(MemoryLayout.PathElement.groupElement("hProcess"))).address();
    }
    catch (IOException e) {
      throw e;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** @return the {@code WaitForSingleObject} result, for example {@link #WAIT_TIMEOUT} */
  public static int waitForSingleObject(long handle, int timeoutMillis) {
    try {
      return (int)Handles.WAIT_FOR_SINGLE_OBJECT.invokeExact(MemorySegment.ofAddress(handle), timeoutMillis);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** @return the exit code, {@link #STILL_ACTIVE} for a running process, or {@code null} when the call fails */
  public static @Nullable Integer getExitCodeProcess(long handle) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment exitCode = arena.allocate(JAVA_INT);
      int succeeded = (int)Handles.GET_EXIT_CODE_PROCESS.invokeExact(MemorySegment.ofAddress(handle), exitCode);
      return succeeded != 0 ? exitCode.get(JAVA_INT, 0) : null;
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** @return 0 on success, else the {@code GetLastError} code */
  public static int terminateProcess(long handle, int exitCode) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment callState = arena.allocate(Handles.CALL_STATE_LAYOUT);
      int succeeded = (int)Handles.TERMINATE_PROCESS.invokeExact(callState, MemorySegment.ofAddress(handle), exitCode);
      return succeeded != 0 ? 0 : (int)Handles.LAST_ERROR.get(callState, 0L);
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  public static void closeHandle(long handle) {
    try {
      int ignored = (int)Handles.CLOSE_HANDLE.invokeExact(MemorySegment.ofAddress(handle));
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** @return the system message for a Win32 error code, trimmed, or an empty string */
  public static @NotNull String formatMessage(int error) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment buffer = arena.allocate(ADDRESS);
      int length = (int)Handles.FORMAT_MESSAGE.invokeExact(
        FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS, MemorySegment.NULL, error, LANG_USER_DEFAULT, buffer, 0, MemorySegment.NULL);
      MemorySegment text = buffer.get(ADDRESS, 0);
      if (length <= 0 || text.address() == 0) {
        return "";
      }
      try {
        return new String(text.reinterpret(2L * length).toArray(JAVA_BYTE), StandardCharsets.UTF_16LE).trim();
      }
      finally {
        MemorySegment ignored = (MemorySegment)Handles.LOCAL_FREE.invokeExact(text);
      }
    }
    catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  /** {@code HANDLE} and every pointer are addresses; {@code BOOL} and {@code DWORD} are {@code int}. */
  private static final class Handles {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 = WindowsSystemLibraries.lookup("kernel32.dll");
    private static final Linker.Option CAPTURE_LAST_ERROR = Linker.Option.captureCallState("GetLastError");

    static final StructLayout CALL_STATE_LAYOUT = Linker.Option.captureStateLayout();
    static final VarHandle LAST_ERROR = CALL_STATE_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

    /** {@code STARTUPINFOW}, 104 bytes on x64 and ARM64 */
    static final StructLayout STARTUPINFOW = MemoryLayout.structLayout(
      JAVA_INT.withName("cb"), MemoryLayout.paddingLayout(4),
      ADDRESS.withName("lpReserved"), ADDRESS.withName("lpDesktop"), ADDRESS.withName("lpTitle"),
      JAVA_INT.withName("dwX"), JAVA_INT.withName("dwY"), JAVA_INT.withName("dwXSize"), JAVA_INT.withName("dwYSize"),
      JAVA_INT.withName("dwXCountChars"), JAVA_INT.withName("dwYCountChars"), JAVA_INT.withName("dwFillAttribute"), JAVA_INT.withName("dwFlags"),
      JAVA_SHORT.withName("wShowWindow"), JAVA_SHORT.withName("cbReserved2"), MemoryLayout.paddingLayout(4),
      ADDRESS.withName("lpReserved2"), ADDRESS.withName("hStdInput"), ADDRESS.withName("hStdOutput"), ADDRESS.withName("hStdError"));
    /** {@code PROCESS_INFORMATION { HANDLE hProcess; HANDLE hThread; DWORD dwProcessId; DWORD dwThreadId; }}, 24 bytes */
    static final StructLayout PROCESS_INFORMATION = MemoryLayout.structLayout(
      ADDRESS.withName("hProcess"), ADDRESS.withName("hThread"), JAVA_INT.withName("dwProcessId"), JAVA_INT.withName("dwThreadId"));

    /** {@code BOOL CreateProcessW(LPCWSTR application, LPWSTR commandLine, LPSECURITY_ATTRIBUTES, LPSECURITY_ATTRIBUTES, BOOL inheritHandles, DWORD flags, LPVOID environment, LPCWSTR directory, LPSTARTUPINFOW, LPPROCESS_INFORMATION)} */
    static final MethodHandle CREATE_PROCESS = LINKER.downcallHandle(
      KERNEL32.findOrThrow("CreateProcessW"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS), CAPTURE_LAST_ERROR);
    /** {@code DWORD WaitForSingleObject(HANDLE, DWORD millis)} */
    static final MethodHandle WAIT_FOR_SINGLE_OBJECT = LINKER.downcallHandle(KERNEL32.findOrThrow("WaitForSingleObject"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    /** {@code BOOL GetExitCodeProcess(HANDLE, LPDWORD)} */
    static final MethodHandle GET_EXIT_CODE_PROCESS = LINKER.downcallHandle(KERNEL32.findOrThrow("GetExitCodeProcess"), FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    /** {@code BOOL TerminateProcess(HANDLE, UINT exitCode)} */
    static final MethodHandle TERMINATE_PROCESS = LINKER.downcallHandle(
      KERNEL32.findOrThrow("TerminateProcess"), FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), CAPTURE_LAST_ERROR);
    /** {@code BOOL CloseHandle(HANDLE)} */
    static final MethodHandle CLOSE_HANDLE = LINKER.downcallHandle(KERNEL32.findOrThrow("CloseHandle"), FunctionDescriptor.of(JAVA_INT, ADDRESS));
    /** {@code DWORD FormatMessageW(DWORD flags, LPCVOID source, DWORD messageId, DWORD languageId, LPWSTR *buffer, DWORD size, va_list *)} */
    static final MethodHandle FORMAT_MESSAGE = LINKER.downcallHandle(
      KERNEL32.findOrThrow("FormatMessageW"), FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    /** {@code HLOCAL LocalFree(HLOCAL)} */
    static final MethodHandle LOCAL_FREE = LINKER.downcallHandle(KERNEL32.findOrThrow("LocalFree"), FunctionDescriptor.of(ADDRESS, ADDRESS));
  }
}
