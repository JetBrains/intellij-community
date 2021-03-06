// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.toolwindow

import com.google.gson.Gson
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.util.text.DateFormatUtil

class StatisticsEventLogMessageBuilder {
  fun buildLogMessage(logEvent: LogEvent): String {
    return buildString {
      append(DateFormatUtil.formatTimeWithSeconds(logEvent.time))
      val event = logEvent.event
      append(" - [\"${logEvent.group.id}\", v${logEvent.group.version}]: \"${event.id}\" ")
      val count = event.count
      if(!event.state && count > 1) {
        append("(count=$count) ")
      }
      append("{")
      append(eventDataToString(event.data))
      append("}")
    }
  }

  private fun eventDataToString(eventData: Map<String, Any>): String {
    return eventData.filter { (key, _) -> !systemFields.contains(key) }
      .map { (key, value) -> "\"$key\":${valueToString(value, key)}" }
      .joinToString(", ")
  }

  private fun valueToString(value: Any, key: String): String =
    when (value) {
      is Map<*, *>, is Collection<*> -> gson.toJson(value)
      else -> {
        var valueAsString = value.toString()
        if (key == "project") {
          valueAsString = shortenProjectId(valueAsString)
        }
        "\"$valueAsString\""
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