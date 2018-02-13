// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.diagnostic.Logger

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.ArrayList

class LogEventContent(val events: List<LogEvent>) {
  val product = ApplicationInfo.getInstance().build.productCode
  val user = PermanentInstallationID.get()

  companion object {
    private val LOG = Logger.getInstance(LogEventContent::class.java)

    fun create(file: File): LogEventContent? {
      try {
        val events = ArrayList<LogEvent>()
        BufferedReader(FileReader(file.path)).use { reader ->
          var line = reader.readLine()
          while (line != null) {
            events.add(LogEventSerializer.fromString(line))
            line = reader.readLine()
          }
        }

        if (!events.isEmpty()) {
          return LogEventContent(events)
        }
      }
      catch (e: IOException) {
        LOG.warn(e)
      }

      return null
    }
  }
}
