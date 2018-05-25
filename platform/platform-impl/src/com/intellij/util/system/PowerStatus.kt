// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.system

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.jna.JnaLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary
import java.io.IOException

enum class PowerStatus {
  UNKNOWN, AC_POWER, BATTERY
}

fun getPowerStatus(): PowerStatus {
  return when {
    SystemInfo.isWindows -> getWindowsPowerStatus()
    SystemInfo.isLinux -> getLinuxPowerStatus()
    SystemInfo.isMac -> getMacPowerStatus()
    else -> PowerStatus.UNKNOWN
  }
}

private fun getWindowsPowerStatus(): PowerStatus {
  if (!JnaLoader.isLoaded()) return PowerStatus.UNKNOWN
  val systemPowerStatus = SYSTEM_POWER_STATUS()
  if (!kernel32.GetSystemPowerStatus(systemPowerStatus)) return PowerStatus.UNKNOWN
  return if (systemPowerStatus.ACLineStatus.toInt() == 1) PowerStatus.AC_POWER else PowerStatus.BATTERY
}

class SYSTEM_POWER_STATUS : Structure() {
  override fun getFieldOrder(): List<String> {
    return listOf("ACLineStatus", "BatteryFlag", "BatteryLifePercent", "SystemStatusFlag", "BatteryLifeTime", "BatteryFullLifeTime")
  }

  @JvmField var ACLineStatus: Byte = 0
  @JvmField var BatteryFlag: Byte = 0
  @JvmField var BatteryLifePercent: Byte = 0
  @JvmField var SystemStatusFlag: Byte = 0
  @JvmField var BatteryLifeTime: Int = 0
  @JvmField var BatteryFullLifeTime: Int = 0
}

private interface Kernel32 : StdCallLibrary {
  fun GetSystemPowerStatus(result: SYSTEM_POWER_STATUS): Boolean
}

private val kernel32 by lazy { Native.loadLibrary("kernel32", Kernel32::class.java) }

// https://github.com/Goles/Battery/blob/master/battery
private fun getLinuxPowerStatus(): PowerStatus {
  val file = batteryFilePath ?: return PowerStatus.UNKNOWN
  try {
    return if (FileUtil.loadFile(file).trim() == "Discharging") PowerStatus.BATTERY else PowerStatus.AC_POWER
  }
  catch (e: IOException) {
    return PowerStatus.UNKNOWN;
  }
}

private val batteryFilePath by lazy {
  FileUtil.findFirstThatExist("/sys/class/power_supply/BAT0/status")
}

fun getMacPowerStatus(): PowerStatus {
  val currentTime = System.currentTimeMillis()
  lastMacPowerStatus?.let {
    if (currentTime - lastMacPowerStatusUpdateTimestamp > Registry.intValue("ide.mac.power.status.refresh")) {
      return it
    }
  }
  val status = updateMacPowerStatus()
  lastMacPowerStatusUpdateTimestamp = currentTime
  lastMacPowerStatus = status
  return status
}

private var lastMacPowerStatus: PowerStatus? = null
private var lastMacPowerStatusUpdateTimestamp: Long = 0

private fun updateMacPowerStatus(): PowerStatus {
  try {
    val output = ExecUtil.execAndGetOutput(GeneralCommandLine("/usr/bin/pmset", "-g", "batt"))
    if (output.exitCode != 0 || output.stderr.isNotEmpty()) return PowerStatus.UNKNOWN
    return when {
      "'AC Power'" in output.stdout -> PowerStatus.AC_POWER
      "'Battery Power'" in output.stdout -> PowerStatus.BATTERY
      else -> PowerStatus.UNKNOWN
    }
  }
  catch (e: ExecutionException) {
    return PowerStatus.UNKNOWN
  }
}

fun main(args: Array<String>) {
  val startTime = System.nanoTime()
  val powerStatus = getPowerStatus()
  val elapsed = (System.nanoTime() - startTime) / 1000
  println("The current power status is $powerStatus, retrieved in $elapsed useс")
}
