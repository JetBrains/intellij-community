// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.warmup.util

import com.intellij.application.subscribe
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

fun <Y> withLoggingProgresses(action: (ProgressIndicator) -> Y): Y {
  val messages = Channel<String>(128)
  val indicator = LoggingProgressIndicator(messages)
  val disposable = Disposer.newDisposable()

  val job = Job()
  Disposer.register(disposable, Disposable { job.cancel() })

  ProgressWindow.TOPIC.subscribe(disposable, ProgressWindow.Listener { pw ->
    pw.addStateDelegate(LoggingProgressIndicator(messages))
  })

  @Suppress("EXPERIMENTAL_API_USAGE")
  GlobalScope.launch(job + Dispatchers.IO) {
    messages.consumeAsFlow()
      .sample(300)
      .distinctUntilChanged()
      .collect {
        ConsoleLog.info(it)
      }
  }

  try {
    return action(indicator)
  }
  finally {
    Disposer.dispose(disposable)
  }
}

private fun trimProgressTextAndNullize(s: String?) = s?.trim()?.trimEnd('.', '\u2026', ' ')?.takeIf { it.isNotBlank() }

private fun progressIndicatorText(progressIndicator: ProgressIndicator): String? {
  val text = trimProgressTextAndNullize(progressIndicator.text)
  val text2 = trimProgressTextAndNullize(progressIndicator.text2)
  if (text.isNullOrBlank() && text2.isNullOrBlank()) {
    return null
  }

  val message = (text ?: "") + (text2?.let { " ($it)" } ?: "")
  if (message.isBlank() || progressIndicator.isIndeterminate) {
    return message.takeIf { it.isNotBlank() }
  }

  val v = (100.0 * progressIndicator.fraction).toInt()
  val total = 18
  val completed = (total * progressIndicator.fraction).toInt().coerceAtLeast(0)
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
    messages.trySend(progressIndicatorText(this@LoggingProgressIndicator) ?: return).onClosed {
      throw IllegalStateException(it)
    }
  }
}
