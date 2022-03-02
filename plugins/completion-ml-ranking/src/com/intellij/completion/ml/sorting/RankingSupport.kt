// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.completion.ml.experiment.ExperimentStatus
import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.completion.ml.ranker.ExperimentModelProvider.Companion.match
import com.intellij.completion.ml.ranker.local.MLCompletionLocalModelsUtil
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.internal.ml.completion.DecoratingItemsPolicy
import com.intellij.internal.ml.completion.RankingModelProvider
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly

object RankingSupport {
  private val LOG = logger<RankingSupport>()
  private var enabledInTests: Boolean = false

  fun getRankingModel(language: Language): RankingModelWrapper? {
    MLCompletionLocalModelsUtil.getModel(language.id)?.let { return LanguageRankingModel(it, DecoratingItemsPolicy.DISABLED) }
    val provider = findProviderSafe(language)
    return if (provider != null && shouldSortByML(language, provider)) tryGetModel(provider) else null
  }

  fun availableRankers(): List<RankingModelProvider> {
    val registeredLanguages = Language.getRegisteredLanguages()
    val experimentStatus = ExperimentStatus.getInstance()
    return ExperimentModelProvider.availableProviders()
      .filter { provider ->
        registeredLanguages.any {
          provider.match(it, experimentStatus.forLanguage(it).version)
        }
      }.toList()
  }

  fun findProviderSafe(language: Language): RankingModelProvider? {
    val experimentInfo = ExperimentStatus.getInstance().forLanguage(language)
    try {
      return ExperimentModelProvider.findProvider(language, experimentInfo.version)
    }
    catch (e: IllegalStateException) {
      LOG.error(e)
      return null
    }
  }

  private fun tryGetModel(provider: RankingModelProvider): RankingModelWrapper? {
    try {
      return LanguageRankingModel(provider.model, provider.decoratingPolicy)
    }
    catch (e: Exception) {
      LOG.error("Could not create ranking model with id '${provider.id}' and name '${provider.displayNameInSettings}'", e)
      return null
    }
  }

  private fun shouldSortByML(language: Language, provider: RankingModelProvider): Boolean {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode) return enabledInTests

    val settings = CompletionMLRankingSettings.getInstance()
    val experimentStatus = ExperimentStatus.getInstance()
    val experimentInfo = experimentStatus.forLanguage(language)
    val shouldSort = settings.isRankingEnabled && settings.isLanguageEnabled(provider.id)

    if (application.isEAP && experimentInfo.inExperiment && !experimentStatus.isDisabled()) {
      settings.updateShowDiffInExperiment(experimentInfo.shouldShowArrows)
    }

    return shouldSort
  }

  @TestOnly
  fun enableInTests(parentDisposable: Disposable) {
    enabledInTests = true
    Disposer.register(parentDisposable, Disposable { enabledInTests = false })
  }
}
