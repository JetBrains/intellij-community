// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ranker

import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.PlatformUtils

class PreloadCatBoostModel : PreloadingActivity() {
  override fun preload(indicator: ProgressIndicator) {
    if (!PlatformUtils.isIntelliJ()) return

    val providers = ExperimentModelProvider.availableProviders().filterIsInstance<CatBoostJarCompletionModelProvider>()
    for (provider in providers) {
      // force to load model
      provider.model
      indicator.checkCanceled()
    }
  }
}