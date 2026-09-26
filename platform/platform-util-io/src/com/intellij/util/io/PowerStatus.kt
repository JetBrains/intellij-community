// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

enum class PowerStatus {
  UNKNOWN, AC, BATTERY;

  companion object {
    @JvmStatic
    fun getPowerStatus(): PowerStatus = PowerService.service.status()
  }
}

internal interface PowerService {
  fun status(): PowerStatus

  companion object {
    internal val LOG: Logger = logger<PowerStatus>()

    val service: PowerService by lazy {
      try {
        when {
          SystemInfo.isWindows -> WinPowerService()
          SystemInfo.isMac -> MacPowerService()
          SystemInfo.isLinux -> LinuxPowerService()
          else -> NullPowerService()
        }
      }
      catch (t: Throwable) {
        LOG.warn(t)
        NullPowerService()
      }
    }
  }
}

private class NullPowerService : PowerService {
  override fun status() = PowerStatus.UNKNOWN
}

//<editor-fold desc="Windows implementation">
private class WinPowerService : PowerService {
  override fun status(): PowerStatus {
    val acLineStatus = WindowsPower.acLineStatus()
    if (PowerService.LOG.isDebugEnabled) PowerService.LOG.debug("ACLineStatus=${acLineStatus}")
    return when (acLineStatus) {
      0 -> PowerStatus.BATTERY
      1 -> PowerStatus.AC
      else -> PowerStatus.UNKNOWN
    }
  }
}
//</editor-fold>

//<editor-fold desc="macOS implementation">
private class MacPowerService : PowerService {
  /**
   * IOKit names the source that supplies the machine now. A discharging UPS counts as a battery.
   * Every other answer is [PowerStatus.AC], because a machine without a battery runs on the AC line.
   */
  override fun status(): PowerStatus {
    val type = MacPower.providingPowerSourceType()
    if (PowerService.LOG.isDebugEnabled) PowerService.LOG.debug("providingPowerSourceType=${type}")
    return when (type) {
      MacPower.BATTERY_POWER, MacPower.UPS_POWER -> PowerStatus.BATTERY
      else -> PowerStatus.AC
    }
  }
}
//</editor-fold>

//<editor-fold desc="Linux implementation">
private class LinuxPowerService : PowerService {
  /**
   * Returns "AC" if there is at least one online source of a type "Mains".
   * Returns "BATTERY" if there is at least one source of a type "Battery" in a discharging state.
   * UPSes don't seem to be represented via SysFS.
   * See [https://github.com/torvalds/linux/blob/master/drivers/power/supply/power_supply_sysfs.c].
   */
  override fun status(): PowerStatus {
    val devices = classDirectory.listFiles() ?: throw IOException("can't enumerate devices")
    if (PowerService.LOG.isDebugEnabled) PowerService.LOG.debug("devices=${devices.size}")

    var online = false
    var discharging = false

    for (device in devices) {
      val type = read(device, "type")
      if (PowerService.LOG.isDebugEnabled) PowerService.LOG.debug("${device.name} type=${type}")
      if (type == "Mains") {
        val state = read(device, "online")
        if (PowerService.LOG.isDebugEnabled) PowerService.LOG.debug("  online=${state}")
        if (state == "1") online = true
      }
      else if (type == "Battery") {
        val state = read(device, "status")
        if (PowerService.LOG.isDebugEnabled) PowerService.LOG.debug("  status=${state}")
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

  private fun read(device: File, key: String): String =
    try { BufferedReader(FileReader(File(device, key))).use { it.readLine() } }
    catch (_: IOException) { "-" }
}
//</editor-fold>
