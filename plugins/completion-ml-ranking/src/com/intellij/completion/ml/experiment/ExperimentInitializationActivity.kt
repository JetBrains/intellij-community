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
    if (!app.isEAP || app.isUnitTestMode || app.isHeadlessEnvironment) {
      return
    }

    val settings = CompletionMLRankingSettings.getInstance()
    val experimentStatus = ExperimentStatus.getInstance()
    val languages = Language.getRegisteredLanguages()
    for (language in languages) {
      val experimentInfo = experimentStatus.forLanguage(language)
      if (experimentInfo.inExperiment) {
        val ranker = RankingSupport.findProviderSafe(language)
        if (ranker != null && experimentStatus.experimentChanged(language)) {
          configureSettingsInExperimentOnce(settings, experimentInfo, ranker.id)
        }
      }
    }
  }

  private fun configureSettingsInExperimentOnce(settings: CompletionMLRankingSettings, experimentInfo: ExperimentInfo, rankerId: String) {
    if (experimentInfo.shouldRank) {
      settings.isRankingEnabled = experimentInfo.shouldRank
    }
    settings.setLanguageEnabled(rankerId, experimentInfo.shouldRank)
    settings.isShowDiffEnabled = experimentInfo.shouldShowArrows
  }
}

