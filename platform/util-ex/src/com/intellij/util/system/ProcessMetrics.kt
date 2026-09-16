// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.util.system

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.jvm.optionals.getOrNull

/**
 * The resident memory and the CPU time of a local process, addressed by its PID.
 *
 * The process usually belongs to the current user, for example a daemon or a profiled application.
 * The readers use the Foreign Function and Memory API and the `/proc` file system, so they need no JNA.
 * A reader that fails once is switched off for the rest of the session. Every reader returns `null` instead of a failure.
 *
 * This object lives here, and not in `intellij.platform.util`, because that module stays at Java 8.
 */
@ApiStatus.Internal
object ProcessMetrics {
  private val LOG = logger<ProcessMetrics>()

  @Volatile
  private var residentSetSizeReaderIsBroken = false

  /**
   * @return the resident set size of the process in bytes, or `null` when the process is gone,
   * the OS refuses the query, or the OS is not supported
   */
  fun residentSetSize(pid: Long): Long? {
    if (residentSetSizeReaderIsBroken) {
      return null
    }
    try {
      return when (OS.CURRENT) {
        OS.Linux -> LinuxProcessStatus.residentSetSize(pid)
        OS.macOS -> MacProcInfo.residentSetSize(pid)
        OS.Windows -> WindowsProcessInfo.residentSetSize(pid)
        else -> null
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      residentSetSizeReaderIsBroken = true
      LOG.warn("The resident set size reader failed and is switched off", e)
      return null
    }
  }

  /**
   * @return the user plus the kernel CPU time of the process, or `null` when the process is gone or the OS does not report it
   */
  fun cpuTime(pid: Long): Duration? {
    return ProcessHandle.of(pid).getOrNull()?.info()?.totalCpuDuration()?.getOrNull()
  }
}

/** Reads `VmRSS` from `/proc/<pid>/status`. The kernel reports the value in kilobytes. */
private object LinuxProcessStatus {
  private const val VM_RSS_PREFIX = "VmRSS:"

  fun residentSetSize(pid: Long): Long? {
    val status = Path.of("/proc", pid.toString(), "status")
    if (!Files.isReadable(status)) {
      return null
    }
    Files.newBufferedReader(status).useLines { lines ->
      val line = lines.firstOrNull { it.startsWith(VM_RSS_PREFIX) } ?: return null
      val kilobytes = line.substring(VM_RSS_PREFIX.length).trim().substringBefore(' ').toLongOrNull() ?: return null
      return kilobytes * 1024
    }
  }
}

/**
 * Reads `pti_resident_size` of `struct proc_taskinfo` through a `proc_pidinfo(pid, PROC_PIDTASKINFO)` downcall.
 * The call works for a process of the current user without `task_for_pid`.
 */
private object MacProcInfo {
  private const val PROC_PIDTASKINFO = 4

  /** `sizeof(struct proc_taskinfo)`: six `uint64_t` fields, then twelve `int32_t` fields. */
  private const val PROC_TASKINFO_SIZE = 96

  /** The offset of `pti_resident_size`, the second `uint64_t` field. */
  private const val PTI_RESIDENT_SIZE = 8L

  /** `int proc_pidinfo(int pid, int flavor, uint64_t arg, void *buffer, int buffersize)`. It returns the byte count it wrote, `0` on failure. */
  private val PROC_PIDINFO: MethodHandle by lazy {
    val linker = Linker.nativeLinker()
    linker.downcallHandle(
      linker.defaultLookup().findOrThrow("proc_pidinfo"),
      FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT),
    )
  }

  fun residentSetSize(pid: Long): Long? {
    Arena.ofConfined().use { arena ->
      val info = arena.allocate(PROC_TASKINFO_SIZE.toLong())
      val written = PROC_PIDINFO.invokeExact(pid.toInt(), PROC_PIDTASKINFO, 0L, info, PROC_TASKINFO_SIZE) as Int
      if (written < PROC_TASKINFO_SIZE) {
        return null
      }
      return info.get(JAVA_LONG, PTI_RESIDENT_SIZE)
    }
  }
}

/**
 * Reads `WorkingSetSize` of `PROCESS_MEMORY_COUNTERS` through `OpenProcess` and a `GetProcessMemoryInfo` downcall into `psapi.dll`.
 * `PROCESS_QUERY_LIMITED_INFORMATION` is enough for the query since Windows 8.1.
 */
private object WindowsProcessInfo {
  private val LOG = logger<WindowsProcessInfo>()

  private const val PROCESS_QUERY_LIMITED_INFORMATION = 0x1000

  /** `sizeof(PROCESS_MEMORY_COUNTERS)` on x64 and ARM64: two `DWORD`s, then eight `SIZE_T`s. */
  private const val COUNTERS_SIZE = 72

  /** The offset of `WorkingSetSize`: after `cb`, `PageFaultCount` and `PeakWorkingSetSize`. */
  private const val WORKING_SET_SIZE = 16L

  private val LINKER: Linker = Linker.nativeLinker()

  /** `HANDLE OpenProcess(DWORD dwDesiredAccess, BOOL bInheritHandle, DWORD dwProcessId)` */
  private val OPEN_PROCESS: MethodHandle by lazy {
    LINKER.downcallHandle(
      WindowsSystemLibraries.lookup("kernel32.dll").findOrThrow("OpenProcess"),
      FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT),
    )
  }

  /** `BOOL CloseHandle(HANDLE hObject)` */
  private val CLOSE_HANDLE: MethodHandle by lazy {
    LINKER.downcallHandle(
      WindowsSystemLibraries.lookup("kernel32.dll").findOrThrow("CloseHandle"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS),
    )
  }

  /** `BOOL GetExitCodeProcess(HANDLE hProcess, LPDWORD lpExitCode)` */
  private val GET_EXIT_CODE_PROCESS: MethodHandle by lazy {
    LINKER.downcallHandle(
      WindowsSystemLibraries.lookup("kernel32.dll").findOrThrow("GetExitCodeProcess"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS),
    )
  }

  /** The exit code `GetExitCodeProcess` reports for a running process. */
  private const val STILL_ACTIVE = 259

  /** `BOOL GetProcessMemoryInfo(HANDLE Process, PPROCESS_MEMORY_COUNTERS ppsmemCounters, DWORD cb)` */
  private val GET_PROCESS_MEMORY_INFO: MethodHandle by lazy {
    LINKER.downcallHandle(
      WindowsSystemLibraries.lookup("psapi.dll").findOrThrow("GetProcessMemoryInfo"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT),
    )
  }

  fun residentSetSize(pid: Long): Long? {
    val process = OPEN_PROCESS.invokeExact(PROCESS_QUERY_LIMITED_INFORMATION, 0, pid.toInt()) as MemorySegment
    if (process == MemorySegment.NULL) {
      return null
    }
    try {
      Arena.ofConfined().use { arena ->
        // A process that exited keeps its object alive while another process holds a handle, and OpenProcess still succeeds.
        // Its working set is gone, so report nothing, like the other OSes do for a dead PID.
        val exitCode = arena.allocate(JAVA_INT)
        val hasExitCode = GET_EXIT_CODE_PROCESS.invokeExact(process, exitCode) as Int
        if (hasExitCode != 0 && exitCode.get(JAVA_INT, 0) != STILL_ACTIVE) {
          return null
        }
        val counters = arena.allocate(COUNTERS_SIZE.toLong())
        counters.set(JAVA_INT, 0, COUNTERS_SIZE)
        val succeeded = GET_PROCESS_MEMORY_INFO.invokeExact(process, counters, COUNTERS_SIZE) as Int
        if (succeeded == 0) {
          return null
        }
        return counters.get(JAVA_LONG, WORKING_SET_SIZE)
      }
    }
    finally {
      val closed = CLOSE_HANDLE.invokeExact(process) as Int
      if (closed == 0) {
        LOG.debug { "CloseHandle failed for the process $pid" }
      }
    }
  }
}
