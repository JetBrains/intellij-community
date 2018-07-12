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

class LogEventRecordRequest(val product : String, val user: String, val records: List<LogEventRecord>) {

  companion object {
    private const val RECORD_SIZE = 1000 * 1000 // 1000KB
    private val LOG = Logger.getInstance(LogEventRecordRequest::class.java)

    fun create(file: File, filter: LogEventFilter): LogEventRecordRequest? {
      try {
        return create(file, ApplicationInfo.getInstance().build.productCode, PermanentInstallationID.get(), RECORD_SIZE, filter)
      }
      catch (e: Exception) {
        LOG.warn("Failed reading event log file", e)
        return null
      }
    }

    fun create(file: File, product: String, user: String, maxRecordSize: Int, filter: LogEventFilter): LogEventRecordRequest? {
      try {
        val records = ArrayList<LogEventRecord>()
        BufferedReader(FileReader(file.path)).use { reader ->
          val sizeEstimator = LogEventRecordSizeEstimator(product, user)
          var events = ArrayList<LogEvent>()
          var line = fillNextBatch(reader, reader.readLine(), events, sizeEstimator, maxRecordSize, filter)
          while (!events.isEmpty()) {
            records.add(LogEventRecord(events))
            events = ArrayList()
            line = fillNextBatch(reader, line, events, sizeEstimator, maxRecordSize, filter)
          }
        }
        return LogEventRecordRequest(product, user, records)
      }
      catch (e: JsonSyntaxException) {
        LOG.warn(e)
      }
      catch (e: IOException) {
        LOG.warn(e)
      }
      return null
    }

    private fun fillNextBatch(reader: BufferedReader,
                              firstLine: String?,
                              events: MutableList<LogEvent>,
                              estimator: LogEventRecordSizeEstimator,
                              maxRecordSize: Int,
                              filter: LogEventFilter) : String? {
      var recordSize = 0
      var line = firstLine
      while (line != null && recordSize + estimator.estimate(line) < maxRecordSize) {
        val event = LogEventSerializer.fromString(line)
        if (event != null && filter.accepts(event.group.id)) {
          recordSize += estimator.estimate(line)
          events.add(event)
        }
        line = reader.readLine()
      }
      return line
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LogEventRecordRequest

    if (product != other.product) return false
    if (user != other.user) return false
    if (records != other.records) return false

    return true
  }

  override fun hashCode(): Int {
    var result = product.hashCode()
    result = 31 * result + user.hashCode()
    result = 31 * result + records.hashCode()
    return result
  }
}

class LogEventRecord(val events: List<LogEvent>) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as LogEventRecord

    if (events != other.events) return false

    return true
  }

  override fun hashCode(): Int {
    return events.hashCode()
  }
}

class LogEventRecordSizeEstimator(product : String, user: String) {
  private val formatAdditionalSize = product.length + user.length + 2

  fun estimate(line: String) : Int {
    return line.length + formatAdditionalSize
  }
}