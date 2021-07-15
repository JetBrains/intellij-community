// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.StatisticsStringUtil
import java.io.File
import java.nio.file.Path

interface EventLogRecorderConfig {
  fun getRecorderId(): String

  fun isSendEnabled(): Boolean

  fun getLogFilesProvider(): EventLogFilesProvider
}

abstract class EventLogFilesProvider {
  abstract fun getLogFilesDir(): Path?

  abstract fun getLogFiles(): List<EventLogFile>

  fun getFilesToSend(maxFilesToSend: Int, activeFile: String?): List<File> {
    val files = getLogFilesDir()?.toFile()?.listFiles { f: File -> activeFile == null || !StatisticsStringUtil.equals(f.name, activeFile) }
    if (files == null) return emptyList()
    val filteredFiles = if (maxFilesToSend == -1) {
      files.toList()
    }
    else {
      files.take(maxFilesToSend)
    }
    return filteredFiles
  }

}

class DefaultEventLogFilesProvider(private val dir: Path,
                                   private val maxFilesToSend: Int,
                                   private val activeFileProvider: () -> String?) : EventLogFilesProvider() {
  override fun getLogFilesDir(): Path = dir

  override fun getLogFiles(): List<EventLogFile> {
    val activeFile = activeFileProvider()
    return getFilesToSend(maxFilesToSend, activeFile).map { EventLogFile(it) }.toList()
  }
}