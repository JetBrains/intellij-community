// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.ApiStatus
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.StructLayout
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.foreign.ValueLayout.JAVA_SHORT
import java.lang.invoke.MethodHandle
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * The command line, the image path and the parent of a local process. See [get].
 */
@ConsistentCopyVisibility
@ApiStatus.Internal
data class WindowsProcessInfo private constructor(
  val pid: Long,
  val commandLine: @NlsSafe String,
  val executable: Path,
  val parentId: Long?,
) {
  /**
   * [commandLine], split into the arguments the process itself sees. The first element is the executable.
   *
   * Windows gives a process one string and lets the process split it, so a split on a space is wrong.
   * It breaks a quoted path such as `"C:\Program Files\app.exe" -c`.
   */
  val arguments: List<@NlsSafe String> by lazy { commandLine.toArgv() }

  companion object {
    /**
     * Reads the process [pid] through its `PEB`. The reader needs `PROCESS_QUERY_INFORMATION` and `PROCESS_VM_READ`.
     *
     * @return the process information, or a failure that names the call and its error code
     */
    @ApiStatus.Internal
    fun get(pid: Long): Result<WindowsProcessInfo> {
      assert(SystemInfoRt.isWindows) { "Do not call on **nix" }

      Arena.ofConfined().use { arena ->
        val callState = arena.allocate(WindowsKernel32.CALL_STATE)
        val access = WindowsKernel32.PROCESS_QUERY_INFORMATION or WindowsKernel32.PROCESS_VM_READ
        val process = WindowsKernel32.openProcess(callState, access, pid)
        if (process.address() == 0L) {
          return winFailure("OpenProcess failed for the PID $pid", WindowsKernel32.lastError(callState))
        }

        try {
          val basic = arena.allocate(WindowsNtDll.PROCESS_BASIC_INFORMATION)
          val status = WindowsNtDll.queryInformationProcess(
            process, WindowsNtDll.PROCESS_BASIC_INFORMATION_CLASS, basic, basic.byteSize().toInt())
          if (status != 0) {
            return winFailure("NtQueryInformationProcess failed for the PID $pid", status)
          }

          val pebAddress = basic.get(ADDRESS, WindowsNtDll.PEB_BASE_ADDRESS)
          val peb = readStruct(callState, process, pebAddress, WindowsNtDll.PEB, arena)
            .getOrElse { return Result.failure(it) }

          val parametersAddress = peb.get(ADDRESS, WindowsNtDll.PROCESS_PARAMETERS)
          val parameters = readStruct(callState, process, parametersAddress, WindowsNtDll.RTL_USER_PROCESS_PARAMETERS, arena)
            .getOrElse { return Result.failure(it) }

          val commandLine = readRemoteString(callState, process, parameters, WindowsNtDll.COMMAND_LINE, arena)
            .getOrElse { return Result.failure(it) }
          val executable = readRemoteString(callState, process, parameters, WindowsNtDll.IMAGE_PATH_NAME, arena)
            .getOrElse { return Result.failure(it) }

          val parentId = basic.get(JAVA_LONG, WindowsNtDll.INHERITED_FROM_UNIQUE_PROCESS_ID)
          return Result.success(WindowsProcessInfo(pid, commandLine, Path(executable), parentId))
        }
        finally {
          WindowsKernel32.closeHandle(process)
        }
      }
    }
  }
}

/**
 * Copies the structure [layout] at [address] from the process [process] into [arena].
 *
 * The alignment comes from the layout, because a later read of a named field needs it.
 */
private fun readStruct(
  callState: MemorySegment, process: MemorySegment, address: MemorySegment, layout: StructLayout, arena: Arena,
): Result<MemorySegment> = readRemote(callState, process, address, layout.byteSize(), layout.byteAlignment(), arena)

/** Copies [size] bytes at [address] from the process [process] into [arena], with the alignment [alignment]. */
private fun readRemote(
  callState: MemorySegment, process: MemorySegment, address: MemorySegment, size: Long, alignment: Long, arena: Arena,
): Result<MemorySegment> {
  if (address.address() == 0L) {
    return winFailure("ReadProcessMemory got a null address", 0)
  }
  val buffer = arena.allocate(size, alignment)
  if (!WindowsKernel32.readProcessMemory(callState, process, address, buffer, size)) {
    return winFailure("ReadProcessMemory failed for $size bytes", WindowsKernel32.lastError(callState))
  }
  return Result.success(buffer)
}

/**
 * Reads the string of the `UNICODE_STRING` at [offset] in [owner].
 *
 * [owner] is a copy of a structure of the process [process], so its `Buffer` field still points into that process
 * and needs one more read. `Length` counts bytes and excludes the terminator, so the copy holds no terminator.
 */
private fun readRemoteString(
  callState: MemorySegment, process: MemorySegment, owner: MemorySegment, offset: Long, arena: Arena,
): Result<String> {
  val length = owner.get(JAVA_SHORT, offset + WindowsNtDll.UNICODE_STRING_LENGTH).toUShort().toLong()
  if (length == 0L) {
    return Result.success("")
  }
  val buffer = owner.get(ADDRESS, offset + WindowsNtDll.UNICODE_STRING_BUFFER)
  val text = readRemote(callState, process, buffer, length, Char.SIZE_BYTES.toLong(), arena)
    .getOrElse { return Result.failure(it) }
  return Result.success(String(text.toArray(JAVA_BYTE), StandardCharsets.UTF_16LE))
}

/**
 * Splits a Windows command line with `CommandLineToArgvW`. Windows itself uses this function, so the result is the
 * argument vector that the process sees.
 *
 * @return the executable and the arguments. An empty list for a blank command line, because `CommandLineToArgvW`
 * reports the executable of the current process for an empty string.
 */
private fun String.toArgv(): List<String> {
  if (isBlank()) {
    return emptyList()
  }
  Arena.ofConfined().use { arena ->
    val count = arena.allocate(JAVA_INT)
    val commandLine = arena.allocateFrom(this, StandardCharsets.UTF_16LE)
    val argv = Shell32.COMMAND_LINE_TO_ARGV.invokeExact(commandLine, count) as MemorySegment
    if (argv.address() == 0L) {
      return emptyList()
    }
    try {
      // The call answers one block: an array of argc pointers, and the strings after it.
      val argc = count.get(JAVA_INT, 0)
      val pointers = argv.reinterpret(argc * ADDRESS.byteSize())
      return (0 until argc).map { index ->
        // Each argument ends with a null character, so getString finds the end itself.
        pointers.getAtIndex(ADDRESS, index.toLong()).reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_16LE)
      }
    }
    finally {
      WindowsKernel32.localFree(argv)
    }
  }
}

/** The one `shell32.dll` downcall of this file. The library loads on the first call. */
private object Shell32 {
  /** `LPWSTR* CommandLineToArgvW(LPCWSTR commandLine, int* count)` */
  val COMMAND_LINE_TO_ARGV: MethodHandle = Linker.nativeLinker().downcallHandle(
    WindowsSystemLibraries.shell32().findOrThrow("CommandLineToArgvW"),
    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS))
}
