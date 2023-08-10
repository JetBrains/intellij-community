// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern

object MachineIdManager {
  private const val IOREG_COMMAND_TIMEOUT_MS = 2000
  private val macMachineIdPattern = Pattern.compile("\"IOPlatformUUID\"\\s*=\\s*\"(?<machineId>.*)\"")
  private val linuxMachineIdPaths = listOf("/etc/machine-id", "/var/lib/dbus/machine-id")

  /**
   * @param purpose What id will be used for, shouldn't be empty.
   * @return Anonymized machine id or null If getting machine id was failed.
   */
  fun getAnonymizedMachineId(purpose: String, salt: String): String? {
    if (purpose.isEmpty()) {
      throw IllegalArgumentException("Argument [purpose] should not be empty.")
    }
    val machineId = getMachineId() ?: return null
    return EventLogConfiguration.hashSha256((System.getProperty("user.name") + purpose + salt).toByteArray(), machineId)
  }

  private fun getMachineId(): String? {
    return try {
      when {
        SystemInfo.isWindows -> {
          Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Cryptography", "MachineGuid")
        }
        SystemInfo.isMac -> {
          getMacOsMachineId()
        }
        SystemInfo.isLinux -> {
          getLinuxMachineId()
        }
        else -> null
      }
    }
    catch (e: Throwable) {
      null
    }
  }


  /**
   * Reads machineId from /etc/machine-id or if not found from /var/lib/dbus/machine-id
   *
   * See https://manpages.debian.org/testing/systemd/machine-id.5.en.html for more details
   */
  private fun getLinuxMachineId(): String? {
    for (machineIdPath in linuxMachineIdPaths) {
      try {
        val machineId = Files.readString(Paths.get(machineIdPath))
        if (!machineId.isNullOrEmpty()) {
          return machineId.trim()
        }
      }
      catch (ignore: IOException) {
      }
    }
    return null
  }


  /**
   * Invokes `ioreg -rd1 -c IOPlatformExpertDevice` to get IOPlatformUUID
   */
  private fun getMacOsMachineId(): String? {
    val commandLine = GeneralCommandLine("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
    val processOutput = ExecUtil.execAndGetOutput(commandLine, IOREG_COMMAND_TIMEOUT_MS)
    if (processOutput.exitCode == 0) {
      val matcher = macMachineIdPattern.matcher(StringUtil.newBombedCharSequence(processOutput.stdout, 1000))
      if (matcher.find()) {
        return matcher.group("machineId")
      }
    }
    return null
  }
}