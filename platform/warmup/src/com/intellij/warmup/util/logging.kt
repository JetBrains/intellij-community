// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.diagnostic.ThreadDumper
import com.intellij.ide.warmup.WarmupStatus
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.JulLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.RollingFileHandler
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManagerListener
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.util.text.Formats
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.ide.bootstrap.logEssentialInfoAboutIde
import com.intellij.platform.util.progress.asContextElement
import com.intellij.platform.util.progress.impl.ProgressState
import com.intellij.platform.util.progress.impl.TextDetailsProgressReporter
import com.intellij.util.application
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
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.io.path.div
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds

object WarmupLogger {
  fun logInfo(message: String) {
    ConsoleLog.info(message)
    warmupLogger?.info(message)
  }

  fun logError(message: String, t: Throwable? = null) {
    ConsoleLog.error(message, t)
    warmupLogger?.error(message, t)
  }

  internal fun logStructured(message: StructuredMessage) {
    ConsoleLog.info(message.fullMessage)
    warmupLogger?.info(message.contractedMessage)
  }
}

internal fun initLogger(args: List<String>) {
  val logger = warmupLogger ?: return
  val info = ApplicationInfo.getInstance()
  val buildDate = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(info.buildDate.time)
  logEssentialInfoAboutIde(log = logger, appInfo = info, args = args)
  val connection = ApplicationManager.getApplication().messageBus.connect()
  connection.subscribe(ProgressManagerListener.TOPIC, WarmupProgressListener())
  logger.info("IDE: ${ApplicationNamesInfo.getInstance().fullProductName} (build #${info.build.asString()}, ${buildDate})")
}


suspend fun <Y> withLoggingProgresses(action: suspend CoroutineScope.(ProgressIndicator) -> Y): Y {
  val indicator = ChannelingProgressIndicator("")

  return coroutineScope {
    action(indicator)
  }
}

private fun trimProgressTextAndNullize(s: String?) = s?.trim()?.trimEnd('.', '\u2026', ' ')?.takeIf { it.isNotBlank() }

internal fun progressStateText(state: ProgressState): StructuredMessage? {
  val text = trimProgressTextAndNullize(state.text)
  val text2 = trimProgressTextAndNullize(state.details)
  if (text.isNullOrBlank() && text2.isNullOrBlank()) {
    return null
  }

  val shortText = text ?: ""
  val verboseText = shortText + (text2?.let { " ($it)" } ?: "")
  if (shortText.isBlank() || state.fraction < 0.0) {
    return StructuredMessage(shortText, verboseText)
  }

  val v = (100.0 * state.fraction).toInt()
  val total = 18
  val completed = (total * state.fraction).toInt().coerceAtLeast(0)
  val d = ".".repeat(completed).padEnd(total, ' ')
  val verboseReport = verboseText.take(100).padEnd(105) + "$d $v%"
  val shortReport = shortText.take(100).padEnd(105) + "$d $v%"
  return StructuredMessage(verboseReport, shortReport)
}

private class ChannelingProgressIndicator(private val prefix: String) : ProgressIndicatorBase() {
  override fun setIndeterminate(indeterminate: Boolean) {
    super.setIndeterminate(indeterminate)
    offerState()
  }

  override fun setFraction(fraction: Double) {
    super.setFraction(fraction)
    offerState()
  }

  override fun setText(text: String?) {
    super.setText(text)
    super.setText2("")
    offerState()
  }

  override fun setText2(text: String?) {
    super.setText2(text)
    offerState()
  }

  private fun offerState() {
    val messages = ApplicationManager.getApplication().service<WarmupLoggingService>().messages
    val progressState = progressStateText(dumpProgressState()) ?: return
    val actualPrefix = if (prefix.isEmpty()) "" else "[$prefix]: "
    messages.tryEmit(progressState.copy(
      contractedMessage = actualPrefix + progressState.contractedMessage,
      fullMessage = actualPrefix + progressState.fullMessage,
    ))
  }
}

private fun ProgressIndicator.dumpProgressState(): ProgressState =
  ProgressState(text = text, details = text2, fraction = if (isIndeterminate) -1.0 else fraction)

/**
 * Installs a progress reporter that sends the information about progress to the stdout instead of UI.
 */
suspend fun <T> withLoggingProgressReporter(action: suspend CoroutineScope.() -> T): T = coroutineScope {
  TextDetailsProgressReporter(this).use { reporter ->
    val job = launch {
      reporter.progressState.collect { progressState ->
        progressStateText(progressState)?.let { WarmupLogger.logStructured(it) }
      }
    }
    try {
      withContext(reporter.asContextElement(), action)
    }
    finally {
      job.cancel()
    }
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
  catch (e: IOException) {
    return@lazyPub null
  }
  val instance = WarmupLoggerFactory()
  ConsoleLog.info("Warmup logs are written to ${WarmupLoggerFactory.basePath}")
  instance
}

private val warmupLogger: Logger? by lazyPub {
  if (WarmupStatus.currentStatus(application) != WarmupStatus.InProgress) {
    null
  }
  else {
    val instance = loggerFactory?.getLoggerInstance("Warmup") ?: return@lazyPub null
    instance
  }
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

class WarmupProgressListener : ProgressManagerListener {

  private val taskDurationMap = ConcurrentHashMap<Int, Long>()


  override fun beforeTaskStart(task: Task, indicator: ProgressIndicator) {
    if (indicator !is ProgressIndicatorEx) {
      return
    }
    WarmupLogger.logInfo("[IDE]: Task '${task.title}' started")
    taskDurationMap[System.identityHashCode(task)] = System.currentTimeMillis()
    indicator.addStateDelegate(ChannelingProgressIndicator("IDE"))
    super.beforeTaskStart(task, indicator)
  }

  override fun afterTaskFinished(task: Task) {
    val currentTime = System.currentTimeMillis()
    val startTime = taskDurationMap.remove(System.identityHashCode(task))
    val elapsedTimeSuffix = if (startTime == null) "" else " in ${Formats.formatDuration(currentTime - startTime)}"
    WarmupLogger.logInfo("[IDE]: Task '${task.title}' ended" + elapsedTimeSuffix)
  }
}

internal fun dumpThreadsAfterConfiguration() {
  val dump = ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), false)
  Path.of(PathManager.getLogPath(), "warmup").findOrCreateFile("thread-dump-after-project-configuration.txt").apply {
    writeText(dump.rawDump)
  }
}