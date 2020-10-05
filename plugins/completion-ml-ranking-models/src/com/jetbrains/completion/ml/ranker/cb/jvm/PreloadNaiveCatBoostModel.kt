package com.jetbrains.completion.ml.ranker.cb.jvm

import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.PlatformUtils

class PreloadNaiveCatBoostModel : PreloadingActivity() {
  override fun preload(indicator: ProgressIndicator) {
    if (!PlatformUtils.isIntelliJ()) return

    val providers = ExperimentModelProvider.availableProviders().filterIsInstance<NaiveCatBoostJarCompletionModelProvider>()
    for (provider in providers) {
      // force to load model
      provider.model
      indicator.checkCanceled()
    }
  }
}