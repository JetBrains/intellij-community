// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.system.OS
import com.sun.jna.platform.mac.IOKitUtil
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.COM.WbemcliUtil
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinReg
import java.nio.file.Path
import kotlin.io.path.readText

object MachineIdManager {
  private val LOG = logger<MachineIdManager>()

  @Deprecated("Use `getAnonymizedMachineId(String)`", level = DeprecationLevel.ERROR)
  fun getAnonymizedMachineId(purpose: String, salt: String): String? = getAnonymizedMachineId(purpose + salt)

  /**
   * @param purpose what the ID will be used for; must not be empty.
   * @return anonymized machine ID, or `null` if getting a machine ID has failed.
   */
  fun getAnonymizedMachineId(purpose: String): String? {
    require (purpose.isNotEmpty()) { "`purpose` should not be empty" }
    return machineId.value?.let { machineId ->
      EventLogConfiguration.hashSha256((System.getProperty("user.name") + purpose).toByteArray(), machineId)
    }
  }

  private val machineId: Lazy<String?> = lazy {
    runCatching {
      when (OS.CURRENT) {
        OS.Windows -> getWindowsMachineId()
        OS.macOS -> getMacOsMachineId()
        OS.Linux -> getLinuxMachineId()
        else -> null
      }
    }.onFailure { LOG.debug(it) }.getOrNull()
  }

  /**
   * See [MachineGuid](https://learn.microsoft.com/en-us/answers/questions/1489139/identifying-unique-windows-installation),
   *     [Win32_ComputerSystemProduct](https://learn.microsoft.com/en-us/windows/win32/cimwin32prov/win32-computersystemproduct).
   */
  private fun getWindowsMachineId(): String? =
    runCatching {
      Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, "SOFTWARE\\Microsoft\\Cryptography", "MachineGuid")
    }.recover {
      LOG.debug(it)
      Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
      WbemcliUtil.WmiQuery("Win32_ComputerSystemProduct", ComputerSystemProductProperty::class.java)
        .execute(2000)
        .let { result ->
          if (result.resultCount > 0) result.getValue(ComputerSystemProductProperty.UUID, 0).toString()
          else null
        }
    }.getOrThrow()

  enum class ComputerSystemProductProperty { UUID }

  /** See [IOPlatformExpertDevice](https://developer.apple.com/documentation/kernel/ioplatformexpertdevice). */
  private fun getMacOsMachineId(): String? =
    IOKitUtil.getMatchingService("IOPlatformExpertDevice")?.let { device ->
      val property = device.getStringProperty("IOPlatformUUID")
      device.release()
      property
    }

  /** See [MACHINE-ID(5)](https://manpages.debian.org/testing/systemd/machine-id.5.en.html). */
  private fun getLinuxMachineId(): String? =
    sequenceOf("/etc/machine-id", "/var/lib/dbus/machine-id", "/sys/devices/virtual/dmi/id/product_uuid")
      .map {
        runCatching { Path.of(it).readText().trim().takeIf(String::isNotEmpty) }
          .onFailure { LOG.debug(it) }
          .getOrNull()
      }
      .firstOrNull()
}
