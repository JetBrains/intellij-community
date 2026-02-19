// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import java.io.File

internal class DefaultFilesToSendProvider(
  recorderId: String,
  private val maxFilesToSend: Int,
  private val filterActiveFile: Boolean,
) : FilesToSendProvider {
  private val eventLoggerProvider = StatisticsEventLogProviderUtil.getEventLogProvider(recorderId)

  override fun getFilesToSend(): List<EventLogFile> {
    val logFilesProvider: EventLogFilesProvider = eventLoggerProvider.getLogFilesProvider()
    val files = if (filterActiveFile) {
      logFilesProvider.getLogFilesExceptActive()
    }
    else {
      logFilesProvider.getLogFiles()
    }
    return getFilesToSend(files, maxFilesToSend).map { EventLogFile(it) }
  }

  private fun getFilesToSend(files: List<File>, maxFilesToSend: Int): List<File> {
    val filteredFiles = if (maxFilesToSend == -1) {
      files.toList()
    }
    else {
      files.take(maxFilesToSend)
    }
    eventLoggerProvider.eventLogSystemLogger.logFileToSendCalculated(files.size, maxFilesToSend, filteredFiles.size)
    return filteredFiles
  }
}