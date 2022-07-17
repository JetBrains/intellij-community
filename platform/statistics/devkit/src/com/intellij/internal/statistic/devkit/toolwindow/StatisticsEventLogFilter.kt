// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.toolwindow

import com.intellij.execution.filters.Filter
import com.intellij.openapi.vfs.VirtualFile
import java.util.regex.Pattern

internal class StatisticsEventLogFilter(private val file: VirtualFile,
                                        private val groupIdToLine: HashMap<String, Int>) : Filter {

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val matcher = LOG_PATTERN.matcher(line)
    if (!matcher.find()) return null

    val groupIdKey = "groupId"
    val groupId = matcher.group(groupIdKey)
    val lineNumber = groupIdToLine[groupId]
    if (lineNumber == null) return null

    val eventId = matcher.group("event")
    val eventData = matcher.group("eventData")
    val groupIdStart = entireLength - line.length
    return Filter.Result(matcher.start(groupIdKey) + groupIdStart,
                         matcher.end(groupIdKey) + groupIdStart,
                         StatisticsGroupHyperlinkInfo(groupId, eventId, eventData, file, lineNumber))
  }

  companion object {
    val LOG_PATTERN: Pattern = Pattern.compile(
      "\\[\"(?<groupId>.*)\", v\\d+]: \"(?<event>.*)\" (?<count>\\(count=\\d+\\))?\\s*(?<eventData>.*)")
  }
}

