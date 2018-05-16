// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.system

import com.intellij.jna.JnaLoader
import com.intellij.openapi.util.SystemInfo
import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.win32.StdCallLibrary

enum class PowerStatus {
  UNKNOWN, AC_POWER, BATTERY
}

fun getPowerStatus(): PowerStatus {
  return when {
    SystemInfo.isWindows -> getWindowsPowerStatus()
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

fun main(args: Array<String>) {
  println("The current power status is ${getPowerStatus()}")
}
