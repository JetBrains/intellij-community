// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.util.system

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.Locale

/**
 * A graphics adapter of the local machine.
 *
 * @property name the marketing name, or [GraphicsCards.UNKNOWN]
 * @property vendor the vendor as the OS reports it, for example `Apple (0x106b)` or `NVIDIA Corporation (0x10de)`, or [GraphicsCards.UNKNOWN]
 * @property deviceId the PCI device id as `0x` plus lowercase hex digits, or [GraphicsCards.UNKNOWN]. Apple silicon reports no device id.
 * @property vramBytes the dedicated video memory in bytes, `0` when the OS reports none or the memory is shared
 */
@ApiStatus.Internal
data class GraphicsCardInfo(
  val name: String,
  val vendor: String,
  val deviceId: String,
  val vramBytes: Long,
)

/**
 * The graphics adapters of the local machine, without JNA.
 *
 * macOS runs `system_profiler SPDisplaysDataType`, Linux runs `lspci`, and Windows queries WMI `Win32_VideoController`.
 * A call takes up to a few seconds, so call it from a background thread and cache the result.
 * A failure is logged once and reported as an empty list.
 */
@ApiStatus.Internal
object GraphicsCards {
  const val UNKNOWN: String = "unknown"

  private val LOG = logger<GraphicsCards>()

  private val DEVICE_ID = Regex("0x[0-9a-f]{1,8}")

  /** `VEN_10DE&DEV_1B80` in a Windows PnP device id, or `[1b80]` in an `lspci` field. */
  private val PNP_VENDOR_DEVICE = Regex("(?:VEN|VID)_([0-9A-Fa-f]{4})&(?:DEV|PID)_([0-9A-Fa-f]{4})")
  private val LSPCI_NAME_AND_ID = Regex("(.+)\\s\\[([0-9a-fA-F]+)]")
  private val LSPCI_MEMORY_SIZE = Regex(".+\\s\\[size=(\\d+)([kKMGT])]")

  fun list(): List<GraphicsCardInfo> {
    try {
      return when (OS.CURRENT) {
        OS.macOS -> parseSystemProfiler(readCommandOutput("system_profiler", "SPDisplaysDataType"))
        OS.Linux -> parseLspci(readCommandOutput("lspci", "-vnnm")) { slot -> readCommandOutput("lspci", "-v", "-s", slot) }
        OS.Windows -> queryWmi()
        else -> emptyList()
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.warn("Cannot list the graphics cards", e)
      return emptyList()
    }
  }

  /**
   * Parses the text output of `system_profiler SPDisplaysDataType`. Every `Chipset Model:` line starts a card.
   * A PCI card also has `Device ID:` and `VRAM (Total):` lines; Apple silicon has neither.
   */
  @VisibleForTesting
  fun parseSystemProfiler(lines: List<String>): List<GraphicsCardInfo> {
    val cards = ArrayList<GraphicsCardInfo>()
    var name: String? = null
    var vendor = UNKNOWN
    var deviceId = UNKNOWN
    var vram = 0L
    fun flush() {
      val cardName = name ?: return
      cards.add(GraphicsCardInfo(name = cardName, vendor = vendor, deviceId = deviceId, vramBytes = vram))
    }
    for (line in lines) {
      val (key, value) = splitKeyValue(line) ?: continue
      when {
        key == "chipset model" -> {
          flush()
          name = value
          vendor = UNKNOWN
          deviceId = UNKNOWN
          vram = 0L
        }
        key == "device id" -> deviceId = normalizeDeviceId(value)
        key == "vendor" -> vendor = value.ifEmpty { UNKNOWN }
        key.startsWith("vram") -> vram = parseMemorySize(value)
      }
    }
    flush()
    return cards
  }

  /**
   * Parses the output of `lspci -vnnm`. A record starts with a `Device:` line that holds the PCI slot and ends with an empty line.
   * Only a record whose `Class:` names a display adapter becomes a card. [memoryLookup] gets the slot and returns the output of
   * `lspci -v -s <slot>`, whose prefetchable memory regions add up to the dedicated video memory.
   */
  @VisibleForTesting
  fun parseLspci(machineReadable: List<String>, memoryLookup: (slot: String) -> List<String>): List<GraphicsCardInfo> {
    val cards = ArrayList<GraphicsCardInfo>()
    var slot: String? = null
    var isDisplayAdapter = false
    var name = UNKNOWN
    var vendor = UNKNOWN
    var deviceId = UNKNOWN
    fun flush() {
      val cardSlot = slot
      if (isDisplayAdapter && cardSlot != null) {
        cards.add(GraphicsCardInfo(name = name, vendor = vendor, deviceId = deviceId, vramBytes = lspciMemorySize(memoryLookup(cardSlot))))
      }
      slot = null
      isDisplayAdapter = false
      name = UNKNOWN
      vendor = UNKNOWN
      deviceId = UNKNOWN
    }
    for (line in machineReadable) {
      val separator = line.indexOf(':')
      if (separator < 0) {
        flush()
        continue
      }
      val key = line.substring(0, separator).trim()
      val value = line.substring(separator + 1).trim()
      when (key) {
        "Device" -> if (slot == null) {
          slot = value
        }
        else {
          val match = LSPCI_NAME_AND_ID.matchEntire(value)
          name = match?.groupValues?.get(1)?.trim() ?: value
          deviceId = match?.let { normalizeDeviceId("0x" + it.groupValues[2]) } ?: UNKNOWN
        }
        "Class" -> isDisplayAdapter = value.contains("VGA") || value.contains("3D controller") || value.contains("Display controller")
        "Vendor" -> {
          val match = LSPCI_NAME_AND_ID.matchEntire(value)
          vendor = if (match != null) "${match.groupValues[1].trim()} (0x${match.groupValues[2].lowercase(Locale.ROOT)})" else value
        }
      }
    }
    flush()
    return cards
  }

  private fun lspciMemorySize(verboseLines: List<String>): Long {
    var bytes = 0L
    for (line in verboseLines) {
      if (!line.contains(" prefetchable")) {
        continue
      }
      val match = LSPCI_MEMORY_SIZE.matchEntire(line) ?: continue
      bytes += parseMemorySize(match.groupValues[1] + " " + match.groupValues[2] + "B")
    }
    return bytes
  }

  private fun queryWmi(): List<GraphicsCardInfo> {
    val rows = WindowsWmi.query("ROOT\\CIMV2", "Win32_VideoController", listOf("Name", "AdapterCompatibility", "PNPDeviceID", "AdapterRAM"), WMI_TIMEOUT_MS)
    return rows.map { row ->
      val pnpDeviceId = row["PNPDeviceID"] as? String ?: ""
      val match = PNP_VENDOR_DEVICE.find(pnpDeviceId)
      val vendor = (row["AdapterCompatibility"] as? String)?.trim()?.ifEmpty { null }
                   ?: match?.let { "0x" + it.groupValues[1].lowercase(Locale.ROOT) }
                   ?: UNKNOWN
      GraphicsCardInfo(
        name = (row["Name"] as? String)?.trim()?.ifEmpty { null } ?: UNKNOWN,
        vendor = vendor,
        deviceId = match?.let { normalizeDeviceId("0x" + it.groupValues[2]) } ?: UNKNOWN,
        // `AdapterRAM` is a `uint32`; WMI hands it over as a signed 32-bit value
        vramBytes = (row["AdapterRAM"] as? Int)?.let { Integer.toUnsignedLong(it) } ?: 0L,
      )
    }
  }

  /** @return `0x` plus lowercase hex digits, or [UNKNOWN] when [raw] is not a device id */
  @VisibleForTesting
  fun normalizeDeviceId(raw: String): String {
    val lower = raw.trim().lowercase(Locale.ROOT)
    return if (DEVICE_ID.matches(lower)) lower else UNKNOWN
  }

  /** Converts `8 GB`, `1536 MB` or `256M` to bytes with binary multipliers. Returns `0` for a text without a leading number. */
  @VisibleForTesting
  fun parseMemorySize(text: String): Long {
    val match = MEMORY_SIZE.find(text.trim()) ?: return 0L
    val value = match.groupValues[1].toLongOrNull() ?: return 0L
    return when (match.groupValues[2].uppercase(Locale.ROOT)) {
      "T" -> value shl 40
      "G" -> value shl 30
      "M" -> value shl 20
      "K" -> value shl 10
      else -> value
    }
  }

  private val MEMORY_SIZE = Regex("^(\\d+)\\s*([kKMGT])?B?")

  /** Splits `  Vendor: Apple (0x106b)` into the lowercase key `vendor` and the trimmed value. */
  internal fun splitKeyValue(line: String): Pair<String, String>? {
    val separator = line.indexOf(':')
    if (separator <= 0) {
      return null
    }
    return line.substring(0, separator).trim().lowercase(Locale.ROOT) to line.substring(separator + 1).trim()
  }

  private const val WMI_TIMEOUT_MS = 10_000
}
