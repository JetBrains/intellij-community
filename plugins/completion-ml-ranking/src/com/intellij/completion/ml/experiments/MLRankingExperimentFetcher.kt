package com.intellij.completion.ml.experiments

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName

@Suppress("PublicApiImplicitType")
interface MLRankingExperimentFetcher {
  fun getExperimentGroup(language: Language): MLRankingExperiment?
  fun getExperimentNumber(language: Language): Int? = getExperimentGroup(language)?.id

  companion object {
    val EP_NAME = ExtensionPointName<MLRankingExperimentFetcher>("com.intellij.completion.ml.experimentFetcher")

    fun getInstance() = EP_NAME.extensionList.firstOrNull()
  } 
}
