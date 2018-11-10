// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io

import com.intellij.jna.JnaLoader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

private val LOG = Logger.getInstance(PowerStatus::class.java)

enum class PowerStatus {
  UNKNOWN, AC, BATTERY;

  companion object {
    @JvmStatic fun getPowerStatus(): PowerStatus =
      try { service.status() }
      catch (t: Throwable) {
        LOG.warn(t)
        UNKNOWN
      }
  }
}

//<editor-fold desc="Implementation loader">
private interface PowerService {
  fun status(): PowerStatus
}

private val service: PowerService by lazy {
  try {
    when {
      SystemInfo.isWindows && JnaLoader.isLoaded() -> WinPowerService()
      SystemInfo.isMac && JnaLoader.isLoaded() -> MacPowerService()
      SystemInfo.isLinux -> LinuxPowerService()
      else -> NullPowerService()
    }
  }
  catch (t: Throwable) {
    LOG.warn(t)
    NullPowerService()
  }
}

private class NullPowerService : PowerService {
  override fun status() = PowerStatus.UNKNOWN
}
//</editor-fold>

//<editor-fold desc="Windows implementation">
@Suppress("ClassName", "PropertyName", "FunctionName", "unused")
private class WinPowerService : PowerService {
  override fun status(): PowerStatus {
    val status = SYSTEM_POWER_STATUS()
    if (!kernel32.GetSystemPowerStatus(status)) {
      throw IOException("GetSystemPowerStatus(): ${kernel32.GetLastError()}")
    }
    else {
      if (LOG.isDebugEnabled) LOG.debug("ACLineStatus=${status.ACLineStatus}")
      return when (status.ACLineStatus.toInt()) {
        0 -> PowerStatus.BATTERY
        1 -> PowerStatus.AC
        else -> PowerStatus.UNKNOWN
      }
    }
  }

  class SYSTEM_POWER_STATUS : Structure() {
    override fun getFieldOrder() =
      listOf("ACLineStatus", "BatteryFlag", "BatteryLifePercent", "SystemStatusFlag", "BatteryLifeTime", "BatteryFullLifeTime")

    @JvmField var ACLineStatus: Byte = 0
    @JvmField var BatteryFlag: Byte = 0
    @JvmField var BatteryLifePercent: Byte = 0
    @JvmField var SystemStatusFlag: Byte = 0
    @JvmField var BatteryLifeTime: Int = 0
    @JvmField var BatteryFullLifeTime: Int = 0
  }

  private interface Kernel32 : StdCallLibrary {
    fun GetSystemPowerStatus(result: SYSTEM_POWER_STATUS): Boolean
    fun GetLastError(): Int
  }

  private val kernel32 = Native.loadLibrary("kernel32", Kernel32::class.java)
}
//</editor-fold>

//<editor-fold desc="macOS implementation">
@Suppress("FunctionName", "unused")
private class MacPowerService : PowerService {
  /**
   * In IOKit, "power sources" include batteries and UPS devices.
   * The method returns "BATTERY" in following cases:
   * - when a system has a battery which is discharging
   * - when a system has no discharging batteries but has a discharging UPS
   */
  override fun status() =
    ioKit.IOPSCopyPowerSourcesInfo().use { psBlob ->
      ioKit.IOPSCopyPowerSourcesList(psBlob).use { psList ->
        var batteryState: PowerStatus? = null
        var upsState: PowerStatus? = null

        val count = ioKit.CFArrayGetCount(psList)
        if (LOG.isDebugEnabled) LOG.debug("count=${count}")
        for (i in 0 until count) {
          val ps = ioKit.IOPSGetPowerSourceDescription(psBlob, ioKit.CFArrayGetValueAtIndex(psList, i))
          if (isTrue(ioKit.CFDictionaryGetValue(ps, kIOPSIsPresentKey))) {
            val type = ioKit.CFDictionaryGetValue(ps, kIOPSTypeKey)
            val state = ioKit.CFDictionaryGetValue(ps, kIOPSPowerSourceStateKey)
            if (LOG.isDebugEnabled) LOG.debug("${i}: type='${str(type)}' state='${str(state)}'")
            if (strEquals(type, kIOPSInternalBatteryType)) {
              batteryState = if (strEquals(state, kIOPSBatteryPowerValue)) PowerStatus.BATTERY else PowerStatus.AC
              break
            }
            else if (strEquals(type, kIOPSUPSType) && upsState === null) {
              upsState = if (strEquals(state, kIOPSBatteryPowerValue)) PowerStatus.BATTERY else PowerStatus.AC
            }
          }
        }

        when {
          batteryState === PowerStatus.BATTERY -> PowerStatus.BATTERY
          upsState !== null -> upsState
          else -> PowerStatus.AC
        }
      }
    } ?: PowerStatus.UNKNOWN

  private interface IOKit : Library {
    fun IOPSCopyPowerSourcesInfo(): Pointer?
    fun IOPSCopyPowerSourcesList(psBlob: Pointer): Pointer?
    fun IOPSGetPowerSourceDescription(psBlob: Pointer, psName:Pointer): Pointer

    fun CFRelease(p: Pointer)
    fun CFArrayGetCount(array: Pointer): Long
    fun CFArrayGetValueAtIndex(array: Pointer, idx: Long): Pointer
    fun CFDictionaryGetValue(dict: Pointer, key: Pointer): Pointer?
    fun CFStringCreateWithCharacters(alloc: Pointer?, chars: CharArray, numChars: Long): Pointer
    fun CFStringCompare(str1: Pointer, str2: Pointer, flags: Long): Long
    fun CFStringGetLength(str: Pointer): Long
    fun CFStringGetCharacters(str: Pointer, range: CFRange, buffer: CharArray)
    fun CFBooleanGetValue(ref: Pointer): Short
  }

  class CFRange : Structure(), Structure.ByValue {
    override fun getFieldOrder() = listOf("location", "length")

    @JvmField var location: Long = 0
    @JvmField var length: Long = 0
  }

  private val ioKit: IOKit = Native.loadLibrary("IOKit", IOKit::class.java)
  private val kIOPSIsPresentKey = CFSTR("Is Present")
  private val kIOPSTypeKey = CFSTR("Type")
  private val kIOPSInternalBatteryType = CFSTR("InternalBattery")
  private val kIOPSUPSType = CFSTR("UPS")
  private val kIOPSPowerSourceStateKey = CFSTR("Power Source State")
  private val kIOPSBatteryPowerValue = CFSTR("Battery Power")
  private val kCFCompareEqualTo = 0L

  private fun CFSTR(str: String) = ioKit.CFStringCreateWithCharacters(null, str.toCharArray(), str.length.toLong())

  private fun isTrue(p: Pointer?) = p !== null && ioKit.CFBooleanGetValue(p).toInt() != 0

  private fun strEquals(p: Pointer?, str: Pointer) = p !== null && ioKit.CFStringCompare(p, str, 0L) == kCFCompareEqualTo

  private fun str(str: Pointer?): String =
    if (str === null) "<null>"
    else {
      val range = CFRange()
      range.length = ioKit.CFStringGetLength(str)
      val buffer = CharArray(range.length.toInt())
      ioKit.CFStringGetCharacters(str, range, buffer)
      String(buffer)
    }

  private inline fun <T> Pointer?.use(block: (Pointer) -> T): T? =
    if (this === null) null
    else try { block(this) } finally { ioKit.CFRelease(this) }
}
//</editor-fold>

//<editor-fold desc="Linux implementation">
private class LinuxPowerService : PowerService {
  /**
   * Returns "AC" if there is at least one online source of type "Mains".
   * Returns "BATTERY" if there is at least one source of type "Battery" in a discharging state.
   * UPSes doesn't seem to be represented via sysfs.
   * See [https://github.com/torvalds/linux/blob/master/drivers/power/supply/power_supply_sysfs.c].
   */
  override fun status(): PowerStatus {
    val devices = classDirectory.listFiles() ?: throw IOException("can't enumerate devices")
    if (LOG.isDebugEnabled) LOG.debug("devices=${devices.size}")

    var online = false
    var discharging = false

    for (device in devices) {
      val type = read(device, "type")
      if (LOG.isDebugEnabled) LOG.debug("${device.name} type=${type}")
      if (type == "Mains") {
        val state = read(device, "online")
        if (LOG.isDebugEnabled) LOG.debug("  online=${state}")
        if (state == "1") online = true
      }
      else if (type == "Battery") {
        val state = read(device, "status")
        if (LOG.isDebugEnabled) LOG.debug("  status=${state}")
        if (state == "Discharging") discharging = true
      }
    }

    return when {
      online -> PowerStatus.AC
      discharging -> PowerStatus.BATTERY
      else -> PowerStatus.UNKNOWN
    }
  }

  private val classDirectory = File("/sys/class/power_supply")

  init {
    if (!classDirectory.isDirectory) throw IOException("not a directory: ${classDirectory}")
  }

  private fun read(device: File, key: String) =
    try { BufferedReader(FileReader(File(device, key))).use { it.readLine() } }
    catch (e: IOException) { "-" }
}
//</editor-fold>