// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.openapi.Disposable
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

interface StatisticsEventLogWriter : Disposable {
  fun log(logEvent: LogEvent)

  fun getActiveFile(): EventLogFile?

  fun getLogFilesProvider(): EventLogFilesProvider

  fun cleanup()

  fun rollOver()
}

class StatisticsEventLogFileWriter(private val recorderId: String,
                                   private val loggerProvider: StatisticsEventLoggerProvider,
                                   maxFileSizeInBytes: Int,
                                   isEap: Boolean,
                                   prefix: String) : StatisticsEventLogWriter {
  private var logger: EventLogFileWriter? = null
    get() {
      return if (loggerProvider.isRecordEnabled()) field else null
    }
    set(value) {
      field?.close()
      field = value
    }

  init {
    try {
      val dir = getEventLogDir()
      val buildType = if (isEap) EventLogBuildType.EAP else EventLogBuildType.RELEASE
      val logFilePathProvider = { directory: Path -> EventLogFile.create(directory, buildType, prefix).file }
      val fileEventLoggerLogger = EventLogFileWriter(dir, maxFileSizeInBytes, logFilePathProvider)
      logger = fileEventLoggerLogger
      if (StatisticsRecorderUtil.isTestModeEnabled(recorderId)) {
        // effectively canceled when this object is disposed
        loggerProvider.coroutineScope.launch {
          delay(10.seconds)
          if (loggerProvider.isRecordEnabled()) fileEventLoggerLogger.flush ()
        }
      }
    }
    catch (e: IOException) {
      System.err.println("Unable to initialize logging for feature usage: " + e.localizedMessage)
    }
  }

  private fun getEventLogDir(): Path {
    return EventLogConfiguration.getInstance().getEventLogDataPath().resolve("logs").resolve(recorderId)
  }

  override fun log(logEvent: LogEvent) {
    logger?.log(LogEventSerializer.toString(logEvent))
  }

  override fun getActiveFile(): EventLogFile? {
    val activeLog = logger?.getActiveLogName() ?: return null
    return EventLogFile(File(File(getEventLogDir().toUri()), activeLog))
  }

  override fun getLogFilesProvider(): EventLogFilesProvider {
    return DefaultEventLogFilesProvider(getEventLogDir()) { logger?.getActiveLogName() }
  }

  override fun cleanup() {
    logger?.cleanUp()
  }

  override fun rollOver() {
    logger?.rollOver()
  }

  override fun dispose() {
    logger = null
  }
}