// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.configuration

import com.intellij.openapi.progress.util.ProgressIndicatorBase
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
class ChannelingProgressIndicator(private val prefix: String) : ProgressIndicatorBase() {
  override fun setIndeterminate(indeterminate: Boolean) {
    super.setIndeterminate(indeterminate)
  }

  private val lastState = AtomicReference(0.0)

  override fun setFraction(fraction: Double) {
    super.setFraction(fraction)
    val lastStateValue = lastState.get()
    if (fraction - lastStateValue > 0.2) {
      // we debounce the messages by 20%
      if (lastState.compareAndSet(lastStateValue, fraction)) {
        offerState()
      }
    }
  }

  override fun setText(text: String?) {
    super.setText(text)
    super.setText2("")
    offerState()
  }

  override fun setText2(text: String?) {
    super.setText2(text)
  }

  private fun trimProgressTextAndNullize(s: String?) = s?.trim()?.trimEnd('.', '\u2026', ' ')?.takeIf { it.isNotBlank() }

  private fun progressStateText(fraction: Double?, text: String?, details: String?): String? {
    val text = trimProgressTextAndNullize(text)
    val text2 = trimProgressTextAndNullize(details)
    if (text.isNullOrBlank() && text2.isNullOrBlank()) {
      return null
    }

    val shortText = text ?: ""
    val verboseText = shortText + (text2?.let { " ($it)" } ?: "")
    if (shortText.isBlank() || fraction == null) {
      return verboseText
    }

    val v = (100.0 * fraction).toInt()
    val total = 18
    val completed = (total * fraction).toInt().coerceIn(0, total)
    val d = ".".repeat(completed).padEnd(total, ' ')
    val verboseReport = verboseText.take(100).padEnd(105) + "$d $v%"
    return verboseReport
  }

  private fun offerState() {
    val progressState = progressStateText(
      fraction = if (isIndeterminate) null else fraction,
      text = text,
      details = text2,
    ) ?: return
    val actualPrefix = if (prefix.isEmpty()) "" else "[$prefix]: "
    HeadlessLogging.logMessage(actualPrefix + progressState)
  }
}