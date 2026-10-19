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

  @Volatile
  private var cpuTimeReaderIsBroken = false

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
        OS.Windows -> WindowsProcessMetrics.residentSetSize(pid)
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
    val handle = ProcessHandle.of(pid).getOrNull() ?: return null
    val cpuTime = handle.info().totalCpuDuration().getOrNull()
    if (cpuTime != null || OS.CURRENT != OS.macOS || cpuTimeReaderIsBroken) {
      return cpuTime
    }
    try {
      return MacProcInfo.cpuTime(pid)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      cpuTimeReaderIsBroken = true
      LOG.warn("The CPU time reader failed and is switched off", e)
      return null
    }
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
 * Reads `pti_resident_size`, `pti_total_user`, and `pti_total_system` of `struct proc_taskinfo`
 * through a `proc_pidinfo(pid, PROC_PIDTASKINFO)` downcall.
 * The call works for a process of the current user without `task_for_pid`.
 *
 * The kernel reports the CPU times in Mach absolute time units, not in nanoseconds.
 * On Intel one unit is one nanosecond, on Apple silicon it is 125/3 nanoseconds, so `mach_timebase_info` supplies the ratio.
 */
private object MacProcInfo {
  private const val PROC_PIDTASKINFO = 4

  /** `sizeof(struct proc_taskinfo)`: six `uint64_t` fields, then twelve `int32_t` fields. */
  private const val PROC_TASKINFO_SIZE = 96

  /** The offset of `pti_resident_size`, the second `uint64_t` field. */
  private const val PTI_RESIDENT_SIZE = 8L
  /** The offsets of `pti_total_user` and `pti_total_system`, the third and the fourth `uint64_t` fields. */
  private const val PTI_TOTAL_USER = 16L
  private const val PTI_TOTAL_SYSTEM = 24L

  /** `int proc_pidinfo(int pid, int flavor, uint64_t arg, void *buffer, int buffersize)`. It returns the byte count it wrote, `0` on failure. */
  private val PROC_PIDINFO: MethodHandle by lazy {
    val linker = Linker.nativeLinker()
    linker.downcallHandle(
      linker.defaultLookup().findOrThrow("proc_pidinfo"),
      FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, ADDRESS, JAVA_INT),
    )
  }

  /** `kern_return_t mach_timebase_info(mach_timebase_info_t info)`. The struct is two `uint32_t` fields, `numer` and `denom`. It returns `0` on success. */
  private val MACH_TIMEBASE_INFO: MethodHandle by lazy {
    val linker = Linker.nativeLinker()
    linker.downcallHandle(
      linker.defaultLookup().findOrThrow("mach_timebase_info"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS),
    )
  }

  /** Nanoseconds per Mach absolute time unit as a `numer / denom` fraction. */
  private val timebase: Pair<Long, Long> by lazy {
    Arena.ofConfined().use { arena ->
      val info = arena.allocate(8L)
      val result = MACH_TIMEBASE_INFO.invokeExact(info) as Int
      check(result == 0) { "mach_timebase_info failed with $result" }
      val numer = info.get(JAVA_INT, 0L).toUInt().toLong()
      val denom = info.get(JAVA_INT, 4L).toUInt().toLong()
      check(numer > 0 && denom > 0) { "mach_timebase_info reported $numer/$denom" }
      numer to denom
    }
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

  fun cpuTime(pid: Long): Duration? {
    Arena.ofConfined().use { arena ->
      val info = arena.allocate(PROC_TASKINFO_SIZE.toLong())
      val written = PROC_PIDINFO.invokeExact(pid.toInt(), PROC_PIDTASKINFO, 0L, info, PROC_TASKINFO_SIZE) as Int
      if (written < PROC_TASKINFO_SIZE) {
        return null
      }
      return Duration.ofNanos(machUnitsToNanos(info.get(JAVA_LONG, PTI_TOTAL_USER) + info.get(JAVA_LONG, PTI_TOTAL_SYSTEM)))
    }
  }

  private fun machUnitsToNanos(units: Long): Long {
    val (numer, denom) = timebase
    // The multiplication is split so that the CPU time of a long-running process does not overflow `units * numer`.
    return units / denom * numer + units % denom * numer / denom
  }
}

/**
 * Reads `WorkingSetSize` of `PROCESS_MEMORY_COUNTERS` through `OpenProcess` and a `GetProcessMemoryInfo` downcall into `psapi.dll`.
 * `PROCESS_QUERY_LIMITED_INFORMATION` is enough for the query since Windows 8.1.
 */
private object WindowsProcessMetrics {
  private val LOG = logger<WindowsProcessMetrics>()

  /** `sizeof(PROCESS_MEMORY_COUNTERS)` on x64 and ARM64: two `DWORD`s, then eight `SIZE_T`s. */
  private const val COUNTERS_SIZE = 72

  /** The offset of `WorkingSetSize`: after `cb`, `PageFaultCount` and `PeakWorkingSetSize`. */
  private const val WORKING_SET_SIZE = 16L

  fun residentSetSize(pid: Long): Long? {
    Arena.ofConfined().use { arena ->
      val callState = arena.allocate(WindowsKernel32.CALL_STATE)
      val process = WindowsKernel32.openProcess(callState, WindowsKernel32.PROCESS_QUERY_LIMITED_INFORMATION, pid)
      if (process.address() == 0L) {
        LOG.debug { "OpenProcess failed for the process $pid: ${WindowsError.prettyHRESULT(WindowsKernel32.lastError(callState))}" }
        return null
      }
      try {
        // A process that exited keeps its object alive while another process holds a handle, and OpenProcess still succeeds.
        // Its working set is gone, so report nothing, like the other OSes do for a dead PID.
        val exitCode = WindowsKernel32.exitCode(process)
        if (exitCode != null && exitCode != WindowsKernel32.STILL_ACTIVE) {
          return null
        }
        val counters = arena.allocate(COUNTERS_SIZE.toLong())
        counters.set(JAVA_INT, 0, COUNTERS_SIZE)
        if (!WindowsPsapi.processMemoryInfo(process, counters)) {
          return null
        }
        return counters.get(JAVA_LONG, WORKING_SET_SIZE)
      }
      finally {
        if (!WindowsKernel32.closeHandle(process)) {
          LOG.debug { "CloseHandle failed for the process $pid" }
        }
      }
    }
  }
}
