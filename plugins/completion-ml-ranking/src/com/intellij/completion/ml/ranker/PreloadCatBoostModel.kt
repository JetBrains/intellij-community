// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.ranker

import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.lang.IdeLanguageCustomization
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator

class PreloadCatBoostModel : PreloadingActivity() {
  override fun preload(indicator: ProgressIndicator) {
    val providers = ExperimentModelProvider.availableProviders()
      .filterIsInstance<CatBoostJarCompletionModelProvider>()
      .filter { it !is ExperimentModelProvider }
    val primaryIdeLanguages = IdeLanguageCustomization.getInstance().primaryIdeLanguages
    for (provider in providers) {
      // force to load model
      if (primaryIdeLanguages.any { provider.isLanguageSupported(it) }) {
        provider.model
        indicator.checkCanceled()
      }
    }
  }
}