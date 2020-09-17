// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.experiment

import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.completion.ml.sorting.RankingSupport
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator

internal class ExperimentInitializationActivity : PreloadingActivity() {

  override fun preload(indicator: ProgressIndicator) {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      return
    }

    val experimentStatus = ExperimentStatus.getInstance()
    if (!app.isEAP || experimentStatus.isDisabled()) {
      return
    }

    val settings = CompletionMLRankingSettings.getInstance()
    val languages = Language.getRegisteredLanguages()
    for (language in languages) {
      val experimentInfo = experimentStatus.forLanguage(language)
      if (experimentInfo.inExperiment) {
        val ranker = RankingSupport.findProviderSafe(language)
        if (ranker != null) {
          settings.updateRankingInExperiment(ranker.id, experimentInfo.shouldRank)
        }
      }
    }
  }
}

