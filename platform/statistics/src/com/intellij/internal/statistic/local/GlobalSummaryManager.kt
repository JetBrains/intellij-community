// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.local

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.text.StringUtil
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * This class manages user FUS events related to Search Everywhere.
 * It uses two sets of data: base statistics and updated statistics from a new IDE release.
 * These different sets are used for different ML models.
 *
 * @property statisticsFile The file with the base data for events.
 * @property updatedStatisticsFile The optional file with the updated data from the newer IDE release.
 */
abstract class GlobalSummaryManager(
  private val statisticsFile: String,
  private val updatedStatisticsFile: String? = null,
) {
  companion object {
    private val QUOTE_FILTER = { ch: Char -> ch != '"' }
    const val DEFAULT_SEPARATOR = ","
  }

  fun getStatistics(id: String): EventGlobalStatistics? =
    statisticsMap[id]

  fun getUpdatedStatistics(id: String): EventGlobalStatistics? =
    updatedStatisticsMap[id]

  private val statisticsMap: Map<String, EventGlobalStatistics> by lazy {
    loadStatistics(statisticsFile)
  }
  private val updatedStatisticsMap: Map<String, EventGlobalStatistics> by lazy {
    loadStatistics(updatedStatisticsFile)
  }

  val eventCountRange: EventCountRange by lazy {
    calculateEventCountRange(statisticsMap)
  }
  val updatedEventCountRange: EventCountRange by lazy {
    calculateEventCountRange(updatedStatisticsMap)
  }

  private fun loadStatistics(filename: String?): Map<String, EventGlobalStatistics> {
    val res = hashMapOf<String, EventGlobalStatistics>()
    if (filename == null) {
      return res
    }
    try {
      javaClass.getResourceAsStream(filename)?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
        reader.lineSequence().forEach { line ->
          val items = line.split(DEFAULT_SEPARATOR)
          val id = StringUtil.trim(items[0], QUOTE_FILTER)
          val users: Long = StringUtil.trim(items[3], QUOTE_FILTER).toLong()
          val allUsers: Long = StringUtil.trim(items[5], QUOTE_FILTER).toLong()
          val events: Long = StringUtil.trim(items[4], QUOTE_FILTER).toLong()
          res[id] = EventGlobalStatistics(users, allUsers, events)
        }
      }
    }
    catch (e: IOException) {
      thisLogger().error("Cannot parse statistics file", e)
    }
    return res
  }

  private fun calculateEventCountRange(statistics: Map<String, EventGlobalStatistics>): EventCountRange {
    var maxCount = 0L
    var minCount = Long.MAX_VALUE
    for ((_, value) in statistics) {
      val frequency = value.eventCount
      maxCount = maxOf(frequency, maxCount)
      minCount = minOf(frequency, minCount)
    }
    return EventCountRange(maxCount, minCount)
  }
}

/**
* This class contains count data for a specific user event. It includes:
*   - `userCount`: The number of distinct users who have triggered the event at least once.
*   - `allUserCount`: All `Search Everywhere` potential users.
*   - `eventCount`: The total number of times this event has been triggered across all users.
*/
data class EventGlobalStatistics(val userCount: Long, val allUserCount: Long, val eventCount: Long) {
  val usersRatio: Double = userCount.toDouble() / allUserCount
  val eventCountPerUserRatio: Double = eventCount.toDouble() / userCount
}

/**
 * This class represents the overall range of frequencies for all user events.
 *   - `maxEventCount`: The frequency of the most commonly triggered event.
 *   - `minEventCount`: The frequency of the least commonly triggered event.
 */
data class EventCountRange(val maxEventCount: Long, val minEventCount: Long)

@Service(Service.Level.APP)
class ActionsGlobalSummaryManager : GlobalSummaryManager(
  "/statistics/actionsUsagesV3.csv",
  "/statistics/actionsUsagesV4.csv") {
  companion object {
    const val STATISTICS_VERSION: Int = 3
    const val UPDATED_STATISTICS_VERSION: Int = 4

    fun getInstance(): ActionsGlobalSummaryManager =
      ApplicationManager.getApplication().getService(ActionsGlobalSummaryManager::class.java)
  }
}

@Service(Service.Level.APP)
class ContributorsGlobalSummaryManager : GlobalSummaryManager(
  statisticsFile = "/statistics/contributorsSelections.csv") {
  companion object {
    fun getInstance(): ContributorsGlobalSummaryManager =
      ApplicationManager.getApplication().getService(ContributorsGlobalSummaryManager::class.java)
  }
}