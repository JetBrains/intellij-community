package com.intellij.remoteDev.util

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.diagnostic.trace
import com.sun.jna.platform.win32.Advapi32
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinError.ERROR_SERVICE_DOES_NOT_EXIST
import com.sun.jna.platform.win32.Winsvc.SC_MANAGER_CONNECT
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object WindowsServiceUtil {

  private val logger = thisLogger()

  fun serviceExists(serviceName: String): Boolean {
    logger.info("Checking if Windows service '$serviceName' exists")
    val svcManager = Advapi32.INSTANCE.OpenSCManager(null, null, SC_MANAGER_CONNECT)
    try {
      val svcHandle = Advapi32.INSTANCE.OpenService(svcManager, serviceName, SC_MANAGER_CONNECT)
      if (svcHandle == null) {
        val error = Kernel32.INSTANCE.GetLastError()
        val errorString = "0x${Integer.toHexString(Kernel32.INSTANCE.GetLastError())}"
        logger.trace { "OpenServiceW returned null with error: $errorString" }

        if (error == ERROR_SERVICE_DOES_NOT_EXIST) {
          return false
        }

        error("Failed to get Windows service. Error: $errorString")
      }

      try {
      } finally {
        Advapi32.INSTANCE.CloseServiceHandle(svcHandle)
      }
    } finally {
      Advapi32.INSTANCE.CloseServiceHandle(svcManager)
    }

    return true
  }
}