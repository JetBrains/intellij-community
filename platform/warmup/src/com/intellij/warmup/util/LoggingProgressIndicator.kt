// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup.util

import com.intellij.application.subscribe
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.ProgressState
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample

@OptIn(FlowPreview::class)
suspend fun <Y> withLoggingProgresses(action: suspend CoroutineScope.(ProgressIndicator) -> Y): Y {
  val messages = Channel<String>(128)
  val indicator = LoggingProgressIndicator(messages)

  return coroutineScope {
    val disposable = Disposer.newDisposable()

    ProgressWindow.TOPIC.subscribe(disposable, ProgressWindow.Listener { pw ->
      pw.addStateDelegate(LoggingProgressIndicator(messages))
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

private class LoggingProgressIndicator(private val messages: SendChannel<String>) : ProgressIndicatorBase() {
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
    messages.trySend(progressStateText(this@LoggingProgressIndicator.dumpProgressState()) ?: return).onClosed {
      throw IllegalStateException(it)
    }
  }
}

private fun ProgressIndicator.dumpProgressState() : ProgressState =
  ProgressState(text = text, details = text2, fraction = if (isIndeterminate) -1.0 else fraction)
