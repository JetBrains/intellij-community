// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.execution.filters.Filter
import com.intellij.execution.ui.ConsoleViewContentType

internal class IntelliJPlatformGradleFilter : Filter {

  companion object {
    private const val PREFIX = "[org.jetbrains.intellij.platform]"
  }

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val idx = line.indexOf(PREFIX)
    if (idx == -1) return null

    val initialOffset = entireLength - line.length
    val items = mutableListOf<Filter.ResultItem>()
    var currentIdx = idx
    while (currentIdx != -1) {
      val startOffset = initialOffset + currentIdx
      val endOffset = startOffset + PREFIX.length
      items.add(Filter.ResultItem(startOffset, endOffset, null, ConsoleViewContentType.LOG_INFO_OUTPUT.attributes))
      currentIdx = line.indexOf(PREFIX, currentIdx + PREFIX.length)
    }

    val result = Filter.Result(items)
    result.nextAction = Filter.NextAction.CONTINUE_FILTERING
    return result
  }
}
