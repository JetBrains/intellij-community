// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.StatisticsStringUtil
import java.io.File
import java.nio.file.Path

interface EventLogRecorderConfig {
  fun getRecorderId(): String

  fun isSendEnabled(): Boolean

  fun getLogFilesProvider(): EventLogFilesProvider
}

interface EventLogFilesProvider {
  fun getLogFilesDir(): Path?

  fun getLogFiles(): List<EventLogFile>
}

class DefaultEventLogFilesProvider(private val dir: Path, private val activeFileProvider: () -> String?): EventLogFilesProvider {
  override fun getLogFilesDir(): Path = dir

  override fun getLogFiles(): List<EventLogFile> {
    val activeFile = activeFileProvider()
    val files = File(dir.toUri()).listFiles { f: File -> activeFile == null || !StatisticsStringUtil.equals(f.name, activeFile) }
    return files?.map { EventLogFile(it) }?.toList() ?: emptyList()
  }
}