// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.config.StatisticsStringUtil
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.Path

@ApiStatus.Internal
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

@ApiStatus.Internal
class DefaultEventLogFilesProvider(
  private val dir: Path,
  private val activeFileProvider: () -> String?,
) : EventLogFilesProvider {
  // Excludes PersistentQueue sidecar files such as `<name>.log.meta` so the external uploader doesn't try to parse them as events
  // (and then delete them, which would lose queue state).
  override fun getLogFiles(): List<File> {
    return dir.toFile().listFiles()?.filter { it.name.endsWith(".log") }?.sortedBy { it.lastModified() }?.toList().orEmpty()
  }

  override fun getLogFilesExceptActive(): List<File> {
    val activeFile = activeFileProvider()
    return getLogFiles().filter { f: File -> activeFile == null || !StatisticsStringUtil.equals(f.name, activeFile) }
  }
}

@ApiStatus.Internal
interface FilesToSendProvider {
  fun getFilesToSend(): List<EventLogFile>
}