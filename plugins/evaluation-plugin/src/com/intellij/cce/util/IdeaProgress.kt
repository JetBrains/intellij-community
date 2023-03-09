package com.intellij.cce.util

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.NlsSafe

class IdeaProgress(private val indicator: ProgressIndicator, val title: String) : Progress {
  override fun isCanceled(): Boolean = indicator.isCanceled

  override fun setProgress(fileName: String, @NlsSafe text: String, fraction: Double) {
    indicator.text2 = text
    indicator.fraction = fraction
  }

  override fun start() {
    indicator.text = title
    indicator.isIndeterminate = false
  }
}
