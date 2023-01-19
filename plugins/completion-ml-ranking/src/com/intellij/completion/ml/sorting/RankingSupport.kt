// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.application.options.CodeCompletionConfigurable
import com.intellij.completion.ml.MLCompletionBundle
import com.intellij.completion.ml.experiment.ExperimentStatus
import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.completion.ml.ranker.ExperimentModelProvider.Companion.match
import com.intellij.completion.ml.ranker.local.MLCompletionLocalModelsUtil
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.ml.completion.DecoratingItemsPolicy
import com.intellij.internal.ml.completion.RankingModelProvider
import com.intellij.lang.Language
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly

object RankingSupport {
  private const val ML_ENABLED_NOTIFICATION_SHOWN_KEY = "completion.ml.enabled.notification.shown"
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
    } else {
      showNotificationAboutMLOnce(shouldSort, provider.isEnabledByDefault, provider.id)
    }

    return shouldSort
  }

  private fun showNotificationAboutMLOnce(shouldSort: Boolean, isEnabledByDefault: Boolean, providerId: String) {
    if (shouldSort && isEnabledByDefault && providerId == "PHP") {
      val properties = PropertiesComponent.getInstance()
      if (!properties.getBoolean(ML_ENABLED_NOTIFICATION_SHOWN_KEY)) {
        properties.setValue(ML_ENABLED_NOTIFICATION_SHOWN_KEY, true)
        MLEnabledNotification().notify(null)
      }
    }
  }

  @TestOnly
  fun enableInTests(parentDisposable: Disposable) {
    enabledInTests = true
    Disposer.register(parentDisposable, Disposable { enabledInTests = false })
  }

  private class MLEnabledNotification : Notification(
    MLCompletionBundle.message("ml.completion.notification.groupId"),
    MLCompletionBundle.message("ml.completion.notification.title"),
    MLCompletionBundle.message("ml.completion.notification.ml.enabled.content"),
    NotificationType.INFORMATION
  ) {
    init {
      addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.enable.decorations")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          val settings = CompletionMLRankingSettings.getInstance()
          settings.isShowDiffEnabled = true
          settings.isDecorateRelevantEnabled = true
          notification.expire()
        }
      })
      addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.configure")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          ShowSettingsUtil.getInstance().showSettingsDialog(null, CodeCompletionConfigurable::class.java)
        }
      })
    }
  }
}
