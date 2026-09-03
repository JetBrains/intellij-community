// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.util

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.diagnostic.trace
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object WindowsServiceUtil {

  private val logger = thisLogger()

  fun serviceExists(serviceName: String): Boolean {
    logger.info("Checking if Windows service '$serviceName' exists")
    val error = WindowsServices.openService(serviceName)
    if (error == 0) {
      return true
    }

    val errorString = "0x${Integer.toHexString(error)}"
    logger.trace { "OpenServiceW returned null with error: $errorString" }
    if (error == WindowsServices.ERROR_SERVICE_DOES_NOT_EXIST) {
      return false
    }

    error("Failed to get Windows service. Error: $errorString")
  }
}
