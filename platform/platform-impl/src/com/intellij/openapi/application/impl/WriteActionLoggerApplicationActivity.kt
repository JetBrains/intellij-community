// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.ide.ApplicationActivity
import com.intellij.openapi.application.WriteActionListener
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger

private class WriteActionLoggerApplicationActivity : ApplicationActivity {
  override suspend fun execute() {
    if (System.getProperty("enable.write.action.logger").toBoolean()) {
      val application = ApplicationManagerEx.getApplicationEx()
      application.addWriteActionListener(WriteActionLogger(), application)
    }
  }
}

private val logger = Logger.getInstance(WriteActionLogger::class.java)

private class WriteActionLogger : WriteActionListener {
  override fun writeActionStarted(action: Class<*>) {
    logger.info("Write action started: $action", Throwable())
  }
}