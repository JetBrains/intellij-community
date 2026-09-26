// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.util.system

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * An installed memory module of the local machine.
 *
 * @property type the module type as the OS reports it, for example `DDR4` or `LPDDR5`, or [MemoryModules.UNKNOWN]
 * @property speedMts the configured data rate in megatransfers per second, or `null` when the OS reports none.
 * The OS labels this number `MHz`, but for DDR memory it is the transfer rate, for example `3200` for DDR4-3200.
 */
@ApiStatus.Internal
data class MemoryModuleInfo(
  val type: String,
  val speedMts: Long?,
)

/**
 * The installed memory modules of the local machine, without JNA.
 *
 * macOS runs `system_profiler SPMemoryDataType`, and Windows queries WMI `Win32_PhysicalMemory`. Linux reports nothing:
 * the module data comes from `dmidecode`, which needs root. A call takes up to a few seconds, so call it from a background
 * thread and cache the result. A failure is logged once and reported as an empty list.
 */
@ApiStatus.Internal
object MemoryModules {
  const val UNKNOWN: String = "unknown"

  private val LOG = logger<MemoryModules>()

  private val LEADING_NUMBER = Regex("^(\\d+)")

  fun list(): List<MemoryModuleInfo> {
    try {
      return when (OS.CURRENT) {
        OS.macOS -> parseSystemProfiler(readCommandOutput("system_profiler", "SPMemoryDataType"))
        OS.Windows -> queryWmi()
        else -> emptyList()
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Cannot list the memory modules", e)
      return emptyList()
    }
  }

  /**
   * Parses the text output of `system_profiler SPMemoryDataType`. An Intel Mac lists one `BANK n/DIMMn:` block per slot with
   * `Type:` and `Speed:` lines. Apple silicon lists one `Type:` line for the whole package and no speed. An empty slot has the
   * type `Empty` and is skipped.
   */
  @VisibleForTesting
  fun parseSystemProfiler(lines: List<String>): List<MemoryModuleInfo> {
    val modules = ArrayList<MemoryModuleInfo>()
    for (line in lines) {
      val (key, value) = GraphicsCards.splitKeyValue(line) ?: continue
      when (key) {
        "type" -> if (!value.equals("Empty", ignoreCase = true)) {
          modules.add(MemoryModuleInfo(type = value.ifEmpty { UNKNOWN }, speedMts = null))
        }
        "speed" -> {
          val last = modules.lastOrNull() ?: continue
          val speed = LEADING_NUMBER.find(value)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0 } ?: continue
          modules[modules.lastIndex] = last.copy(speedMts = speed)
        }
      }
    }
    return modules
  }

  private fun queryWmi(): List<MemoryModuleInfo> {
    val rows = WindowsWmi.query("ROOT\\CIMV2", "Win32_PhysicalMemory", listOf("SMBIOSMemoryType", "Speed"), WMI_TIMEOUT_MS)
    return rows.map { row ->
      MemoryModuleInfo(
        type = smbiosMemoryType(row["SMBIOSMemoryType"] as? Int ?: 0),
        speedMts = (row["Speed"] as? Int)?.let { Integer.toUnsignedLong(it) }?.takeIf { it > 0 },
      )
    }
  }

  /** Maps a memory type code from SMBIOS table 17 (DSP0134, table 77) to its name. */
  @VisibleForTesting
  fun smbiosMemoryType(code: Int): String {
    return when (code) {
      0x12 -> "DDR"
      0x13 -> "DDR2"
      0x14 -> "DDR2 FB-DIMM"
      0x18 -> "DDR3"
      0x1A -> "DDR4"
      0x1B -> "LPDDR"
      0x1C -> "LPDDR2"
      0x1D -> "LPDDR3"
      0x1E -> "LPDDR4"
      0x20 -> "HBM"
      0x21 -> "HBM2"
      0x22 -> "DDR5"
      0x23 -> "LPDDR5"
      0x24 -> "HBM3"
      else -> UNKNOWN
    }
  }

  private const val WMI_TIMEOUT_MS = 10_000
}
