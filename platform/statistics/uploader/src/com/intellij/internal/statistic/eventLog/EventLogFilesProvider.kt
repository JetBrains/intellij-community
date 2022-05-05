// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.config.StatisticsStringUtil
import java.io.File
import java.nio.file.Path

interface EventLogRecorderConfig {
  fun getRecorderId(): String

  fun isSendEnabled(): Boolean

  fun getFilesToSendProvider(): FilesToSendProvider
}

interface EventLogFilesProvider {
  fun getLogFiles(): List<File>
  fun getLogFilesExceptActive(): List<File>
}

class DefaultEventLogFilesProvider(private val dir: Path,
                                   private val activeFileProvider: () -> String?) : EventLogFilesProvider {
  override fun getLogFiles(): List<File> {
    return dir.toFile().listFiles()?.toList().orEmpty()
  }

  override fun getLogFilesExceptActive(): List<File> {
    val activeFile = activeFileProvider()
    return getLogFiles().filter { f: File -> activeFile == null || !StatisticsStringUtil.equals(f.name, activeFile) }
  }
}

interface FilesToSendProvider {
  fun getFilesToSend(): List<EventLogFile>
}

class DefaultFilesToSendProvider(private val logFilesProvider: EventLogFilesProvider,
                                 private val maxFilesToSend: Int,
                                 private val filterActiveFile: Boolean) : FilesToSendProvider {
  override fun getFilesToSend(): List<EventLogFile> {
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
    return filteredFiles
  }
}