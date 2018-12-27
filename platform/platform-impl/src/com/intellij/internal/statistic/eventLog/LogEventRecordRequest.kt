// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.google.gson.JsonSyntaxException
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.prefs.Preferences

class LogEventRecordRequest(val product : String, val user: String, val records: List<LogEventRecord>) {

  companion object {
    private const val RECORD_SIZE = 1000 * 1000 // 1000KB
    private const val USER_ID_KEY = "StatisticsUserId"
    private val LOG = Logger.getInstance(LogEventRecordRequest::class.java)
    private val userId = getOrCreateUserId()

    private fun getOrCreateUserId(): String {
      val preferences = Preferences.userNodeForPackage(LogEventRecordRequest::class.java)
      val userId = preferences.get(USER_ID_KEY, "")
      if (!userId.isEmpty()) return userId
      val calculatedId: String = calculateId(Calendar.getInstance(), getOSByte())
      preferences.put(USER_ID_KEY, calculatedId)
      return calculatedId
    }

    /**
     * Generated user id is the result of [UUID.toString] call, where [UUID] is composed using the following 16 bytes:
     * Bytes from 0 to 9 are randomly generated and represents actual user.
     * 10th byte stands for OS of the machine (see [getOSByte])
     * 11 and 12th bytes contains date of user id creation (see [getDateBytes])
     * Remaining bytes are random and reserved fo future usage.
     */
    fun calculateId(calendar: Calendar, OSByte: Byte): String {
      val bytes = getRandomBytes()
      bytes[10] = OSByte
      val dateBytes = getDateBytes(calendar)
      bytes[11] = dateBytes[0]
      bytes[12] = dateBytes[1]
      val bb = ByteBuffer.wrap(bytes)
      return UUID(bb.long, bb.long).toString()
    }

    /**
     * Return date encoded into 2 bytes with following layout:
     * First 5 bits are day int value, next 4 bits are month and last 7 bits are (year - 2000) % 128
     */
    private fun getDateBytes(calendar: Calendar): ByteArray {
      val day = calendar.get(Calendar.DAY_OF_MONTH) shl 4 + 7 // 5 bit
      val month = calendar.get(Calendar.MONTH) shl 7 // 4 bit
      val year = (calendar.get(Calendar.YEAR) - 2000) % 128 // 7 bit
      return ByteBuffer.allocate(4).putInt(day or month or year).array().sliceArray(listOf(2, 3))
    }

    private fun getOSByte() = (if (SystemInfo.isWindows) 1 else if (SystemInfo.isMac) 2 else if (SystemInfo.isLinux) 3 else 0).toByte()

    private fun getRandomBytes(): ByteArray {
      val randomUUID = UUID.randomUUID()
      val bb = ByteBuffer.wrap(ByteArray(16))
      bb.putLong(randomUUID.mostSignificantBits)
      bb.putLong(randomUUID.leastSignificantBits)
      return bb.array()
    }

    fun create(file: File, filter: LogEventFilter): LogEventRecordRequest? {
      try {
        return create(file, ApplicationInfo.getInstance().build.productCode, userId, RECORD_SIZE, filter)
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