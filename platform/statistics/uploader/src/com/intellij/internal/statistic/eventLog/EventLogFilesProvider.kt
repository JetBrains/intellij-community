// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.config.StatisticsStringUtil
import java.io.File
import java.nio.file.Path

interface EventLogSendConfig {
  fun getRecorderId(): String

  fun getDeviceId(): String

  fun getBucket(): Int

  fun getMachineId(): MachineId

  fun isSendEnabled(): Boolean

  fun isEscapingEnabled(): Boolean = true

  fun getFilesToSendProvider(): FilesToSendProvider
}

interface EventLogFilesProvider {
  fun getLogFiles(): List<File>
  fun getLogFilesExceptActive(): List<File>
}

class DefaultEventLogFilesProvider(
  private val dir: Path,
  private val activeFileProvider: () -> String?,
) : EventLogFilesProvider {
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