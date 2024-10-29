// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.JulLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.RollingFileHandler
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.configuration.ChannelingProgressIndicator
import com.intellij.openapi.project.configuration.HeadlessLogging
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.platform.ide.bootstrap.logEssentialInfoAboutIde
import com.intellij.platform.util.progress.createProgressPipe
import com.intellij.util.lazyPub
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import java.io.IOException
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.io.path.div
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds

internal object WarmupLogger {
  fun logInfo(message: String) {
    warmupLogger?.info(message)
    ConsoleLog.info(message)
  }

  fun logError(message: String, t: Throwable? = null) {
    warmupLogger?.error(message, t)
    ConsoleLog.error(message, t)
  }

  internal fun logStructured(message: StructuredMessage) {
    warmupLogger?.info(message.fullMessage)
    ConsoleLog.info(message.fullMessage)
  }
}

internal fun CoroutineScope.initLogger(args: List<String>): Job {
  val logger = warmupLogger ?: return Job()
  val info = ApplicationInfo.getInstance()
  val buildDate = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(info.buildDate.time)
  logEssentialInfoAboutIde(log = logger, appInfo = info, args = args)
  logger.info("IDE: ${ApplicationNamesInfo.getInstance().fullProductName} (build #${info.build.asString()}, ${buildDate})")
  return launch {
    HeadlessLogging.loggingFlow().collect { (level, message) ->
      val messageRepresentation = message.representation()
      when (level) {
        HeadlessLogging.SeverityKind.Info -> WarmupLogger.logInfo(messageRepresentation)
        HeadlessLogging.SeverityKind.Warning -> WarmupLogger.logInfo(messageRepresentation)
        HeadlessLogging.SeverityKind.Fatal -> WarmupLogger.logError(messageRepresentation)
      }
    }
  }
}


suspend fun <Y> withLoggingProgresses(action: suspend CoroutineScope.(ProgressIndicator) -> Y): Y {
  val indicator = ChannelingProgressIndicator("")

  return coroutineScope {
    action(indicator)
  }
}

private fun trimProgressTextAndNullize(s: String?) = s?.trim()?.trimEnd('.', '\u2026', ' ')?.takeIf { it.isNotBlank() }

internal fun progressStateText(fraction: Double?, text: String?, details: String?): StructuredMessage? {
  val text = trimProgressTextAndNullize(text)
  val text2 = trimProgressTextAndNullize(details)
  if (text.isNullOrBlank() && text2.isNullOrBlank()) {
    return null
  }

  val shortText = text ?: ""
  val verboseText = shortText + (text2?.let { " ($it)" } ?: "")
  if (shortText.isBlank() || fraction == null) {
    return StructuredMessage(shortText, verboseText)
  }

  val v = (100.0 * fraction).toInt()
  val total = 18
  val completed = (total * fraction).toInt().coerceAtLeast(0)
  val d = ".".repeat(completed).padEnd(total, ' ')
  val verboseReport = verboseText.take(100).padEnd(105) + "$d $v%"
  val shortReport = shortText.take(100).padEnd(105) + "$d $v%"
  return StructuredMessage(verboseReport, shortReport)
}



/**
 * Installs a progress reporter that sends the information about progress to the stdout instead of UI.
 */
suspend fun <T> withLoggingProgressReporter(action: suspend CoroutineScope.() -> T): T = coroutineScope {
  val pipe = createProgressPipe()
  val job = launch {
    pipe.progressUpdates().collect { progressState ->
      progressStateText(progressState.fraction, progressState.text, progressState.details)?.let { WarmupLogger.logStructured(it) }
    }
  }
  try {
    pipe.collectProgressUpdates(action)
  }
  finally {
    job.cancel()
  }
}


private class WarmupLoggerFactory : Logger.Factory {

  companion object {
    val basePath: Path = Path.of(PathManager.getLogPath()) / "warmup" / "warmup.log"
  }

  private val appender: RollingFileHandler = RollingFileHandler(basePath, 20_000_000, 50, false)

  override fun getLoggerInstance(category: String): Logger {
    require(category == "Warmup")
    val logger = java.util.logging.Logger.getLogger(category)
    logger.addHandler(appender)
    appender.formatter = object : java.util.logging.Formatter() {
      override fun format(record: LogRecord): String {
        return String.format("%1\$10tT %2\$s%n", record.millis, record.message)
      }
    }
    logger.useParentHandlers = false
    logger.level = Level.INFO
    return JulLogger(logger)
  }
}

private val loggerFactory: WarmupLoggerFactory? by lazyPub {
  try {
    WarmupLoggerFactory.basePath.findOrCreateFile()
  }
  catch (_: IOException) {
    return@lazyPub null
  }
  val instance = WarmupLoggerFactory()
  ConsoleLog.info("Warmup logs are written to ${WarmupLoggerFactory.basePath}")
  instance
}

private val warmupLogger: Logger? by lazyPub {
  val instance = loggerFactory?.getLoggerInstance("Warmup") ?: return@lazyPub null
  instance
}

internal data class StructuredMessage(
  // a complete message as it is shown in the UI ide
  val fullMessage: String,
  // a short message, suitable for logging as it does not contain sensitive information
  val contractedMessage: String,
)

@OptIn(FlowPreview::class)
@Service(Service.Level.APP)
private class WarmupLoggingService(scope: CoroutineScope) {
  val messages = MutableSharedFlow<StructuredMessage>(replay = 0, extraBufferCapacity = 1024, onBufferOverflow = BufferOverflow.DROP_OLDEST)


  init {
    scope.launch(Dispatchers.IO) {
      messages.sample(300.milliseconds).distinctUntilChanged().collect {
        WarmupLogger.logStructured(it)
      }
    }
  }
}


internal fun dumpThreadsAfterConfiguration() {
  val dump = ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), false)
  Path.of(PathManager.getLogPath(), "warmup").findOrCreateFile("thread-dump-after-project-configuration.txt").apply {
    writeText(dump.rawDump)
  }
}