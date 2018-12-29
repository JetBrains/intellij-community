// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.google.gson.JsonSyntaxException
import com.intellij.internal.statistic.eventLog.LogEventRecordRequest.Companion.toString
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PermanentInstallationID.getIdSynchronizedWithDotNetProducts
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.prefs.Preferences

class LogEventRecordRequest(val product : String, val user: String, val records: List<LogEventRecord>) {

  companion object {
    private const val RECORD_SIZE = 1000 * 1000 // 1000KB
    private val LOG = Logger.getInstance(LogEventRecordRequest::class.java)
    private val userId = UserIdManager.getOrCreateUserId()

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

object UserIdManager {
  private const val USER_ID_KEY = "StatisticsUserId"
  private val LOG = Logger.getInstance(UserIdManager::class.java)

  fun getOrCreateUserId(): String {
    val preferences = Preferences.userNodeForPackage(LogEventRecordRequest::class.java)
    val userId = preferences.get(USER_ID_KEY, null) ?: saveToPreferences(preferences, calculateId(Calendar.getInstance(), getOSChar()))
    return if (isVendorJetBrains())
      getIdSynchronizedWithDotNetProducts(preferences, USER_ID_KEY, userId)
    else userId
  }

  private fun isVendorJetBrains(): Boolean {
    try {
      return ApplicationInfoImpl.getShadowInstance().isVendorJetBrains
    }
    catch (e: Exception) {
      LOG.warn(e)
      return false
    }
  }

  /**
   * Generated user id the string, that is result of concatenating following values:
   * Current date, written in format ddMMyy, where year coerced between 2000 and 2099
   * Character, representing user's OS (see [getOSChar])
   * [toString] call on representation of [UUID.randomUUID]
   */
  fun calculateId(calendar: Calendar, OSChar: Char): String {
    calendar.set(Calendar.YEAR, calendar.get(Calendar.YEAR).coerceIn(2000, 2099))
    return SimpleDateFormat("ddMMyy").format(calendar.time) + OSChar + UUID.randomUUID().toString()
  }

  private fun saveToPreferences(preferences: Preferences, id: String): String {
    preferences.put(USER_ID_KEY, id)
    return id
  }

  private fun getOSChar() = if (SystemInfo.isWindows) '1' else if (SystemInfo.isMac) '2' else if (SystemInfo.isLinux) '3' else '0'
}