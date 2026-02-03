// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.toolwindow

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.util.text.DateFormatUtil
import com.jetbrains.fus.reporting.model.lion3.LogEvent

class StatisticsEventLogMessageBuilder {
  fun buildLogMessage(logEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?): String {
    return buildString {
      append(DateFormatUtil.formatTimeWithSeconds(logEvent.time))
      val event = logEvent.event
      val groupId = logEvent.group.id
      val eventId = formatValue(rawEventId, logEvent.event.id)
      append(" - [\"$groupId\", v${logEvent.group.version}]: \"$eventId\"")
      val count = event.count
      if (!event.state && count > 1) {
        append(" (count=$count)")
      }
      append(" {")
      append(eventDataToString(event.data, rawData))
      append("}")
    }
  }

  private fun formatValue(rawValue: String?, validatedValue: String) =
    if (rawValue != null && StatisticsEventLogToolWindow.rejectedValidationTypes.any { it.description == validatedValue }) {
      "$validatedValue[$rawValue]"
    }
    else {
      validatedValue
    }

  private fun eventDataToString(eventData: Map<String, Any>, rawData: Map<String, Any>?): String {
    return eventData.toSortedMap(compareBy { it })
      .filter { (key, _) -> !systemFields.contains(key) }
      .map { (key, value) -> "\"$key\":${valueToString(key, value, rawData?.get(key))}" }
      .joinToString(", ")
  }

  private fun valueToString(key: String, value: Any, rawValue: Any?): String? =
    ObjectMapper().writeValueAsString(prepareValue(key, value, rawValue))

  private fun prepareValue(key: String, value: Any?, rawValue: Any?): Any? {
    return when (value) {
      is Map<*, *> -> {
        value.entries.sortedBy { it.key.toString() }.associate {
          val rawValuesMap = rawValue as? Map<*, *>
          it.key to prepareValue(it.key as String, it.value, rawValuesMap?.get(it.key))
        }
      }
      is List<*> -> value.mapIndexed { index, element ->
        val rawValuesList = rawValue as? List<*>
        prepareValue(key, element, rawValuesList?.get(index))
      }
      is String -> formatValue(rawValue?.toString(), if (fieldsToShorten.contains(key)) shortenAnonymizedId(value) else value)
      else -> value
    }
  }

  private fun shortenAnonymizedId(anonymizedId: String): String {
    val length = anonymizedId.length
    val isRejected = StatisticsEventLogToolWindow.rejectedValidationTypes.any { it.description == anonymizedId }
    if (!isRejected && anonymizedId.isNotBlank() && length > maxProjectIdSize) {
      return "${anonymizedId.substring(0, anonymizedIdPrefixSize)}...${anonymizedId.substring(length - anonymizedIdSuffixSize, length)}"
    }
    else {
      return anonymizedId
    }
  }

  companion object {
    private val systemFields = setOf("last", "created", "system_event_id", "system_headless")
    internal val fieldsToShorten = setOf("project", "file_path", "login_hash", "anonymous_id")
    private const val anonymizedIdPrefixSize = 8
    private const val anonymizedIdSuffixSize = 2
    private const val maxProjectIdSize = anonymizedIdPrefixSize + anonymizedIdSuffixSize
  }
}