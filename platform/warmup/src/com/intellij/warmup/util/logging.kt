// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.application.subscribe
import com.intellij.ide.warmup.WarmupStatus
import com.intellij.idea.logEssentialInfoAboutIde
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.JulLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.RollingFileHandler
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.asContextElement
import com.intellij.openapi.progress.impl.ProgressState
import com.intellij.openapi.progress.impl.TextDetailsProgressReporter
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.util.application
import com.intellij.util.lazyPub
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import java.io.IOException
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.SimpleFormatter
import kotlin.io.path.div
import kotlin.time.Duration.Companion.milliseconds

object WarmupLogger {
  fun logInfo(message: String) {
    ConsoleLog.info(message)
    warmupLogger?.info(message)
  }

  fun logError(message: String, t : Throwable? = null) {
    ConsoleLog.error(message, t)
    warmupLogger?.error(message, t)
  }
}

internal fun initLogger(args: List<String>) {
  val logger = warmupLogger ?: return
  val info = ApplicationInfo.getInstance()
  val buildDate = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(info.buildDate.time)
  logEssentialInfoAboutIde(logger, info, args)
  logger.info("IDE: ${ApplicationNamesInfo.getInstance().fullProductName} (build #${info.build.asString()}, ${buildDate})")
}

@OptIn(FlowPreview::class)
suspend fun <Y> withLoggingProgresses(action: suspend CoroutineScope.(ProgressIndicator) -> Y): Y {
  val messages = Channel<String>(128)
  val indicator = ChannelingProgressIndicator(messages)

  return coroutineScope {
    val disposable = Disposer.newDisposable()

    ProgressWindow.TOPIC.subscribe(disposable, ProgressWindow.Listener { pw ->
      pw.addStateDelegate(ChannelingProgressIndicator(messages))
    })

    @Suppress("EXPERIMENTAL_API_USAGE")
    val job = launch(Dispatchers.IO) {
      messages.consumeAsFlow()
        .sample(300)
        .distinctUntilChanged()
        .collect {
          ConsoleLog.info(it)
        }
    }
    job.invokeOnCompletion { Disposer.dispose(disposable) }

    try {
      action(indicator)
    }
    finally {
      job.cancelAndJoin()
    }
  }
}

private fun trimProgressTextAndNullize(s: String?) = s?.trim()?.trimEnd('.', '\u2026', ' ')?.takeIf { it.isNotBlank() }

internal fun progressStateText(state: ProgressState): String? {
  val text = trimProgressTextAndNullize(state.text)
  val text2 = trimProgressTextAndNullize(state.details)
  if (text.isNullOrBlank() && text2.isNullOrBlank()) {
    return null
  }

  val message = (text ?: "") + (text2?.let { " ($it)" } ?: "")
  if (message.isBlank() || state.fraction < 0.0) {
    return message.takeIf { it.isNotBlank() }
  }

  val v = (100.0 * state.fraction).toInt()
  val total = 18
  val completed = (total * state.fraction).toInt().coerceAtLeast(0)
  val d = ".".repeat(completed).padEnd(total, ' ')
  return message.take(75).padEnd(79) + "$d $v%"
}

private class ChannelingProgressIndicator(private val messages: SendChannel<String>) : ProgressIndicatorBase() {
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
    messages.trySend(progressStateText(dumpProgressState()) ?: return).onClosed {
      throw IllegalStateException(it)
    }
  }
}

private fun ProgressIndicator.dumpProgressState() : ProgressState =
  ProgressState(text = text, details = text2, fraction = if (isIndeterminate) -1.0 else fraction)

/**
 * Installs a progress reporter that sends the information about progress to the stdout instead of UI.
 */
suspend fun <T> withLoggingProgressReporter(action: suspend CoroutineScope.() -> T): T = coroutineScope {
  TextDetailsProgressReporter(this).use { reporter ->
    val reportToCommandLineJob = reportLogsJob(reporter.progressState)
    try {
      withContext(reporter.asContextElement(), action)
    }
    finally {
      reportToCommandLineJob.cancel()
    }
  }
}

@OptIn(FlowPreview::class)
private fun CoroutineScope.reportLogsJob(
  stateFlow: Flow<ProgressState>
): Job {
  return launch(Dispatchers.IO) {
    stateFlow.sample(300.milliseconds).distinctUntilChanged().collect { state ->
      val text = progressStateText(state)
      text?.let {
        ConsoleLog.info(it)
        warmupLogger?.info(it)
      }
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

private val loggerFactory : WarmupLoggerFactory? by lazyPub {
  try {
    WarmupLoggerFactory.basePath.findOrCreateFile()
  } catch (e : IOException) {
    return@lazyPub null
  }
  val instance = WarmupLoggerFactory()
  ConsoleLog.info("Warmup logs are written to ${WarmupLoggerFactory.basePath}")
  instance
}

private val warmupLogger: Logger? by lazyPub {
  if (WarmupStatus.currentStatus(application) != WarmupStatus.InProgress) {
    null
  } else {
    val instance = loggerFactory?.getLoggerInstance("Warmup") ?: return@lazyPub null
    instance
  }
}
