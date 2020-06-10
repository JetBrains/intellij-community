// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.google.gson.JsonSyntaxException
import com.intellij.internal.statistic.eventLog.filters.LogEventFilter
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.*

class LogEventRecordRequest(val recorder: String, val product : String, val device: String, val records: List<LogEventRecord>, val internal: Boolean) {

  companion object {
    private const val RECORD_SIZE = 1000 * 1000 // 1000KB

    fun create(file: File, recorder: String, product: String, deviceId: String, filter: LogEventFilter,
               internal: Boolean, logger: DataCollectorDebugLogger): LogEventRecordRequest? {
      try {
        return create(file, recorder, product, deviceId, RECORD_SIZE, filter, internal, logger)
      }
      catch (e: Exception) {
        logger.warn("Failed reading event log file", e)
        return null
      }
    }

    fun create(file: File, recorder: String, product: String, user: String, maxRecordSize: Int,
               filter: LogEventFilter, internal: Boolean, logger: DataCollectorDebugLogger): LogEventRecordRequest? {
      try {
        val deserializer = LogEventDeserializer(logger)
        val records = ArrayList<LogEventRecord>()
        BufferedReader(FileReader(file.path)).use { reader ->
          val sizeEstimator = LogEventRecordSizeEstimator(product, user)
          var events = ArrayList<LogEvent>()
          var line = fillNextBatch(reader, reader.readLine(), events, deserializer, sizeEstimator, maxRecordSize, filter)
          while (!events.isEmpty()) {
            records.add(LogEventRecord(events))
            events = ArrayList()
            line = fillNextBatch(reader, line, events, deserializer, sizeEstimator, maxRecordSize, filter)
          }
        }
        return LogEventRecordRequest(recorder, product, user, records, internal)
      }
      catch (e: JsonSyntaxException) {
        logger.warn(e.message ?: "", e)
      }
      catch (e: IOException) {
        logger.warn(e.message ?: "", e)
      }
      return null
    }

    private fun fillNextBatch(reader: BufferedReader,
                              firstLine: String?,
                              events: MutableList<LogEvent>,
                              deserializer: LogEventDeserializer,
                              estimator: LogEventRecordSizeEstimator,
                              maxRecordSize: Int,
                              filter: LogEventFilter) : String? {
      var recordSize = 0
      var line = firstLine
      while (line != null && recordSize + estimator.estimate(line) < maxRecordSize) {
        val event = deserializer.fromString(line)
        if (event != null && filter.accepts(event)) {
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

    if (recorder != other.recorder) return false
    if (product != other.product) return false
    if (device != other.device) return false
    if (internal != other.internal) return false
    if (records != other.records) return false

    return true
  }

  override fun hashCode(): Int {
    var result = recorder.hashCode()
    result = 31 * result + product.hashCode()
    result = 31 * result + device.hashCode()
    result = 31 * result + internal.hashCode()
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