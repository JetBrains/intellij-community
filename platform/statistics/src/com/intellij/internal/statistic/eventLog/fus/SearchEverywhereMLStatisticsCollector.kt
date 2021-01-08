// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.fus.SearchEverywhereLogger.log
import com.intellij.openapi.util.registry.Registry

object SearchEverywhereMLStatisticsCollector {
  private const val DIALOG_CLOSED = "dialogClosed"
  private const val SESSION_FINISHED = "sessionFinished"
  private const val TYPED_SYMBOL_KEYS = "typedSymbolKeys"
  private const val TYPED_BACKSPACES_DATA_KEY = "typedBackspaces"
  const val SESSION_ID_LOG_DATA_KEY = "sessionId"
  private const val COLLECTED_RESULTS_DATA_KEY = "collectedItems"
  private const val SELECTED_INDEXES_DATA_KEY = "selectedIndexes"
  private const val TOTAL_SYMBOLS_AMOUNT_DATA_KEY = "totalSymbolsAmount"
  const val IS_ACTION_DATA_KEY = "isAction"
  const val PRIORITY_DATA_KEY = "priority"
  private const val REPORTED_ITEMS_LIMIT = 50
  private const val TOTAL_NUMBER_OF_ITEMS = "totalItems"

  @JvmStatic
  fun reportSelectedElements(sessionId: Int, indexes: IntArray) {
    val logData = FeatureUsageData()
    logData.addData(SESSION_ID_LOG_DATA_KEY, sessionId)
    logData.addData(SELECTED_INDEXES_DATA_KEY, indexes.contentToString())
    log(SESSION_FINISHED, logData.build())
  }

  @JvmStatic
  fun reportSessionEnded(sessionId: Int, symbolsTyped: Int, backspacesTyped: Int, symbolsInQuery: Int, items: List<ItemInfo>) {
    val percentage = Registry.get("statistics.mlse.report.percentage").asInteger() / 100.0
    if (Math.random() < percentage) { // only report a part of cases
      return
    }
    val logData = FeatureUsageData()
    logData.addData(SESSION_ID_LOG_DATA_KEY, sessionId)
    logData.addData(TOTAL_NUMBER_OF_ITEMS, items.size)
    logData.addData(TYPED_SYMBOL_KEYS, symbolsTyped)
    logData.addData(TYPED_BACKSPACES_DATA_KEY, backspacesTyped)
    logData.addData(TOTAL_SYMBOLS_AMOUNT_DATA_KEY, symbolsInQuery)
    val data = logData.build()
    (data as? MutableMap)?.put(COLLECTED_RESULTS_DATA_KEY, items.take(REPORTED_ITEMS_LIMIT).map { it.toMap() })
    log(DIALOG_CLOSED, data)
  }

  data class ItemInfo(val id: String, val contributorId: String, val additionalData: Map<String, Any>) {
    fun toMap(): Map<String, Any> {
      return mapOf("id" to id,
                   "contributorId" to contributorId,
                   "additionalData" to additionalData)
    }
  }
}