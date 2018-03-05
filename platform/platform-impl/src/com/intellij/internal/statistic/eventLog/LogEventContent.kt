// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.*

class LogEventContent(val product : String, val user: String, val events: List<LogEvent>) {

  companion object {
    private const val BATCH_SIZE = 500
    private val LOG = Logger.getInstance(LogEventContent::class.java)

    fun create(file: File): List<LogEventContent> {
      return create(file, ApplicationInfo.getInstance().build.productCode, PermanentInstallationID.get(), BATCH_SIZE)
    }

    fun create(file: File, product: String, user: String, batchSize: Int): List<LogEventContent> {
      try {
        val batches = ArrayList<LogEventContent>()
        BufferedReader(FileReader(file.path)).use { reader ->
          var events = readNextBatch(reader, batchSize)
          while (!events.isEmpty()) {
            batches.add(LogEventContent(product, user, events))
            events = readNextBatch(reader, batchSize)
          }
        }
        return batches
      }
      catch (e: JsonSyntaxException) {
        LOG.warn(e)
      }
      catch (e: IOException) {
        LOG.warn(e)
      }
      return Collections.emptyList()
    }

    private fun readNextBatch(reader : BufferedReader, batchSize: Int) : List<LogEvent> {
      val events = ArrayList<LogEvent>()
      var line = reader.readLine()
      while (line != null) {
        events.add(LogEventSerializer.fromString(line))
        line = if (events.size < batchSize) reader.readLine() else null
      }
      return events
    }
  }
}
