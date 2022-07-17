// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.warmup.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger

object ConsoleLog {
  private val LOG = Logger.getInstance(ConsoleLog::class.java)

  fun info(message: String) {
    LOG.info(message)
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      println(message)
    }
  }

  fun warn(message: String) {
    LOG.warn(message)
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      println("WARN - $message")
    }
  }

  fun error(message: String, cause: Throwable? = null) {
    LOG.error(message, cause)
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      println("ERROR - $message")
      cause?.printStackTrace()
    }
  }
}