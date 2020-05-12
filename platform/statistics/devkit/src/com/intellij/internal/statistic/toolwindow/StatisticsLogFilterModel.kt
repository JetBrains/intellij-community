// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.toolwindow

import com.intellij.diagnostic.logging.LogFilter
import com.intellij.diagnostic.logging.LogFilterListener
import com.intellij.diagnostic.logging.LogFilterModel
import com.intellij.execution.process.ProcessOutputType
import com.intellij.util.containers.ContainerUtil

internal class StatisticsLogFilterModel : LogFilterModel() {
  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<LogFilterListener>()
  private var customFilter: String? = null


  override fun getCustomFilter(): String? = customFilter

  override fun addFilterListener(listener: LogFilterListener?) {
    listeners.add(listener)
  }

  override fun removeFilterListener(listener: LogFilterListener?) {
    listeners.remove(listener)
  }

  override fun getLogFilters(): List<LogFilter> = emptyList()

  override fun isFilterSelected(filter: LogFilter?): Boolean = false

  override fun selectFilter(filter: LogFilter?) {}

  override fun updateCustomFilter(filter: String) {
    super.updateCustomFilter(filter)
    customFilter = filter
    for (listener in listeners) {
      listener.onTextFilterChange()
    }
  }

  override fun processLine(line: String): MyProcessingResult {
    val contentType = defineContentType(line)
    val applicable = isApplicable(line)
    return MyProcessingResult(contentType, applicable, null)
  }

  private fun defineContentType(line: String): ProcessOutputType {
    return when {
      StatisticsEventLogToolWindow.rejectedValidationTypes.any { line.contains(it.description) } -> ProcessOutputType.STDERR
      else -> ProcessOutputType.STDOUT
    }
  }

}