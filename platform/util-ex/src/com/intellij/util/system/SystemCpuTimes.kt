// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.util.system

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle
import java.nio.file.Files
import java.nio.file.Path

/**
 * The CPU time all cores of the local machine spent since boot, in milliseconds.
 *
 * The two counters only grow. A caller samples them twice and divides the busy delta by the sum of both deltas to get the load.
 */
@ApiStatus.Internal
data class CpuTimes(val busyMillis: Long, val idleMillis: Long)

/**
 * Reads [CpuTimes] without JNA: `/proc/stat` on Linux, a `host_statistics` downcall on macOS, and a `GetSystemTimes` downcall
 * on Windows. A reader that fails once is switched off for the rest of the session and returns `null`.
 */
@ApiStatus.Internal
object SystemCpuTimes {
  private val LOG = logger<SystemCpuTimes>()

  @Volatile
  private var readerIsBroken = false

  fun read(): CpuTimes? {
    if (readerIsBroken) {
      return null
    }
    try {
      return when (OS.CURRENT) {
        OS.Linux -> parseProcStat(Files.readAllLines(Path.of("/proc/stat")))
        OS.macOS -> MacHostStatistics.read()
        OS.Windows -> WindowsSystemTimes.read()
        else -> null
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      readerIsBroken = true
      LOG.warn("The system CPU times reader failed and is switched off", e)
      return null
    }
  }

  /**
   * Parses the `cpu` line of `/proc/stat`: `user nice system idle iowait irq softirq steal`, in `USER_HZ` ticks of 10 ms.
   * The idle time includes the I/O wait, the busy time includes everything else.
   */
  @VisibleForTesting
  fun parseProcStat(lines: List<String>): CpuTimes? {
    val cpuLine = lines.firstOrNull { it.startsWith("cpu ") } ?: return null
    val fields = cpuLine.split(' ').filter { it.isNotEmpty() }.drop(1).mapNotNull { it.toLongOrNull() }
    if (fields.size < 4) {
      return null
    }
    val idle = fields[3] + fields.getOrElse(4) { 0L }
    val busy = fields.withIndex().filter { it.index != 3 && it.index != 4 && it.index < 8 }.sumOf { it.value }
    return CpuTimes(busyMillis = busy * PROC_STAT_TICK_MS, idleMillis = idle * PROC_STAT_TICK_MS)
  }

  private const val PROC_STAT_TICK_MS = 10L
}

/** `host_statistics(mach_host_self(), HOST_CPU_LOAD_INFO, ...)` fills four `natural_t` tick counters in units of 10 ms. */
private object MacHostStatistics {
  private const val HOST_CPU_LOAD_INFO = 3
  private const val CPU_STATE_COUNT = 4
  private const val CPU_STATE_USER = 0
  private const val CPU_STATE_SYSTEM = 1
  private const val CPU_STATE_IDLE = 2
  private const val CPU_STATE_NICE = 3
  private const val TICK_MS = 10L

  private val LINKER: Linker = Linker.nativeLinker()

  /** `mach_port_t mach_host_self(void)` */
  private val MACH_HOST_SELF: MethodHandle by lazy {
    LINKER.downcallHandle(LINKER.defaultLookup().findOrThrow("mach_host_self"), FunctionDescriptor.of(JAVA_INT))
  }

  /** `kern_return_t host_statistics(host_t, host_flavor_t, host_info_t out, mach_msg_type_number_t *count)` */
  private val HOST_STATISTICS: MethodHandle by lazy {
    LINKER.downcallHandle(
      LINKER.defaultLookup().findOrThrow("host_statistics"),
      FunctionDescriptor.of(JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS),
    )
  }

  fun read(): CpuTimes? {
    Arena.ofConfined().use { arena ->
      val info = arena.allocate(JAVA_INT.byteSize() * CPU_STATE_COUNT)
      val count = arena.allocate(JAVA_INT)
      count.set(JAVA_INT, 0, CPU_STATE_COUNT)
      val host = MACH_HOST_SELF.invokeExact() as Int
      val result = HOST_STATISTICS.invokeExact(host, HOST_CPU_LOAD_INFO, info, count) as Int
      if (result != 0) {
        return null
      }
      fun tick(state: Int): Long = Integer.toUnsignedLong(info.getAtIndex(JAVA_INT, state.toLong()))
      val busy = tick(CPU_STATE_USER) + tick(CPU_STATE_SYSTEM) + tick(CPU_STATE_NICE)
      return CpuTimes(busyMillis = busy * TICK_MS, idleMillis = tick(CPU_STATE_IDLE) * TICK_MS)
    }
  }
}

/** `GetSystemTimes` returns three `FILETIME`s in 100 ns units. The kernel time includes the idle time. */
private object WindowsSystemTimes {
  private const val FILETIME_UNITS_PER_MS = 10_000L

  /** `BOOL GetSystemTimes(LPFILETIME lpIdleTime, LPFILETIME lpKernelTime, LPFILETIME lpUserTime)` */
  private val GET_SYSTEM_TIMES: MethodHandle by lazy {
    Linker.nativeLinker().downcallHandle(
      WindowsSystemLibraries.lookup("kernel32.dll").findOrThrow("GetSystemTimes"),
      FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS),
    )
  }

  fun read(): CpuTimes? {
    Arena.ofConfined().use { arena ->
      val idle = arena.allocate(JAVA_LONG)
      val kernel = arena.allocate(JAVA_LONG)
      val user = arena.allocate(JAVA_LONG)
      val succeeded = GET_SYSTEM_TIMES.invokeExact(idle, kernel, user) as Int
      if (succeeded == 0) {
        return null
      }
      val idleMs = idle.get(JAVA_LONG, 0) / FILETIME_UNITS_PER_MS
      val kernelMs = kernel.get(JAVA_LONG, 0) / FILETIME_UNITS_PER_MS
      val userMs = user.get(JAVA_LONG, 0) / FILETIME_UNITS_PER_MS
      return CpuTimes(busyMillis = (kernelMs - idleMs).coerceAtLeast(0) + userMs, idleMillis = idleMs)
    }
  }
}
