// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.toolwindow

import com.google.gson.Gson
import com.intellij.util.text.DateFormatUtil
import com.jetbrains.fus.reporting.model.lion3.LogEvent

class StatisticsEventLogMessageBuilder {
  fun buildLogMessage(logEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?): String {
    return buildString {
      append(DateFormatUtil.formatTimeWithSeconds(logEvent.time))
      val event = logEvent.event
      val groupId = logEvent.group.id
      val eventId = formatValue(rawEventId, logEvent.event.id)
      append(" - [\"$groupId\", v${logEvent.group.version}]: \"$eventId\" ")
      val count = event.count
      if (!event.state && count > 1) {
        append("(count=$count) ")
      }
      append("{")
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
    return eventData.filter { (key, _) -> !systemFields.contains(key) }
      .map { (key, value) -> "\"$key\":${valueToString(key, value, rawData?.get(key))}" }
      .joinToString(", ")
  }

  private fun valueToString(key: String, value: Any, rawValue: Any?): String = gson.toJson(prepareValue(key, value, rawValue))

  private fun prepareValue(key: String, value: Any?, rawValue: Any?): Any? {
    return when (value) {
      is Map<*, *> -> {
        value.entries.associate {
          val rawValuesMap = rawValue as? Map<*, *>
          it.key to prepareValue(it.key as String, it.value, rawValuesMap?.get(it.key))
        }
      }
      is List<*> -> value.mapIndexed { index, element ->
        val rawValuesList = rawValue as? List<*>
        prepareValue(key, element, rawValuesList?.get(index))
      }
      is String -> formatValue(rawValue?.toString(), if (key == "project") shortenProjectId(value.toString()) else value)
      else -> value
    }
  }

  private fun shortenProjectId(projectId: String): String {
    val length = projectId.length
    val isRejected = StatisticsEventLogToolWindow.rejectedValidationTypes.any { it.description == projectId }
    if (!isRejected && projectId.isNotBlank() && length > maxProjectIdSize) {
      return "${projectId.substring(0, projectIdPrefixSize)}...${projectId.substring(length - projectIdSuffixSize, length)}"
    }
    else {
      return projectId
    }
  }

  companion object {
    private val gson = Gson()
    private val systemFields = setOf("last", "created", "system_event_id", "system_headless")
    private const val projectIdPrefixSize = 8
    private const val projectIdSuffixSize = 2
    private const val maxProjectIdSize = projectIdPrefixSize + projectIdSuffixSize
  }
}