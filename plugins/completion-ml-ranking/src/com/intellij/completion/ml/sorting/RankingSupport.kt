// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.completion.ml.MLCompletionBundle
import com.intellij.completion.ml.experiment.ExperimentInfo
import com.intellij.completion.ml.experiment.ExperimentStatus
import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.completion.ml.ranker.ExperimentModelProvider.Companion.match
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.completion.ml.settings.MLCompletionSettingsCollector
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.ml.completion.RankingModelProvider
import com.intellij.lang.Language
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.completion.ranker.model.MLCompletionLocalModelsUtil
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicInteger

object RankingSupport {
  private const val SHOW_ARROWS_NOTIFICATION_REGISTRY = "completion.ml.show.arrows.notification"
  private const val ARROWS_NOTIFICATION_SHOWN_KEY = "completion.ml.arrows.notification.shown"
  private const val ARROWS_NOTIFICATION_SESSIONS_COUNT = 50
  private val LOG = logger<RankingSupport>()
  private var enabledInTests: Boolean = false
  private val sessionsWithArrowsCounter = AtomicInteger()

  fun getRankingModel(language: Language): RankingModelWrapper? {
    MLCompletionLocalModelsUtil.getModel(language.id)?.let { return LanguageRankingModel(it) }
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

  private fun findProviderSafe(language: Language): RankingModelProvider? {
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
      return LanguageRankingModel(provider.model)
    }
    catch (e: Exception) {
      LOG.error("Could not create ranking model with id '${provider.id}' and name '${provider.displayNameInSettings}'", e)
      return null
    }
  }

  private fun shouldSortByML(language: Language, provider: RankingModelProvider): Boolean {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode) return enabledInTests
    val experimentStatus = ExperimentStatus.getInstance()
    val experimentInfo = experimentStatus.forLanguage(language)
    if (application.isEAP && experimentInfo.inExperiment && experimentStatus.experimentChanged(language)) {
      configureSettingsInExperimentOnce(experimentInfo, provider.id)
    }

    val settings = CompletionMLRankingSettings.getInstance()
    val shouldSortByML = settings.isRankingEnabled && settings.isLanguageEnabled(provider.id)
    if (shouldSortByML) {
      showArrowsNotificationIfNeeded(settings)
    }
    return shouldSortByML
  }

  private fun configureSettingsInExperimentOnce(experimentInfo: ExperimentInfo, rankerId: String) {
    val settings = CompletionMLRankingSettings.getInstance()
    if (experimentInfo.shouldRank) settings.isRankingEnabled = experimentInfo.shouldRank
    settings.setLanguageEnabled(rankerId, experimentInfo.shouldRank)
    settings.isShowDiffEnabled = experimentInfo.shouldShowArrows
    if (experimentInfo.shouldShowArrows && shouldShowArrowsNotification()) {
      showNotificationAboutArrows()
    }
  }

  private fun showArrowsNotificationIfNeeded(settings: CompletionMLRankingSettings) {
    val properties = PropertiesComponent.getInstance()
    if (settings.isShowDiffEnabled && shouldShowArrowsNotification() && !properties.getBoolean(ARROWS_NOTIFICATION_SHOWN_KEY)) {
      val sessionsCount = sessionsWithArrowsCounter.incrementAndGet()
      if (sessionsCount == ARROWS_NOTIFICATION_SESSIONS_COUNT) {
        properties.setValue(ARROWS_NOTIFICATION_SHOWN_KEY, true)
        showNotificationAboutArrows()
      }
    }
  }

  private fun shouldShowArrowsNotification(): Boolean = Registry.`is`(SHOW_ARROWS_NOTIFICATION_REGISTRY, true)

  private fun showNotificationAboutArrows() {
    Notification(
      MLCompletionBundle.message("ml.completion.notification.groupId"),
      MLCompletionBundle.message("ml.completion.notification.decorating.title"),
      MLCompletionBundle.message("ml.completion.notification.decorating.content"),
      NotificationType.INFORMATION
    ).addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.decorating.like")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          MLCompletionSettingsCollector.decorationOpinionProvided(MLCompletionSettingsCollector.DecorationOpinion.LIKE)
          notification.expire()
        }
      })
      .addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.decorating.dislike")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          MLCompletionSettingsCollector.decorationOpinionProvided(MLCompletionSettingsCollector.DecorationOpinion.DISLIKE)
          notification.expire()
        }
      })
      .addAction(object : NotificationAction(MLCompletionBundle.message("ml.completion.notification.decorating.dunno")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          MLCompletionSettingsCollector.decorationOpinionProvided(MLCompletionSettingsCollector.DecorationOpinion.DONT_KNOWN)
          notification.expire()
        }
      }).notify(null)
  }

  @TestOnly
  fun enableInTests(parentDisposable: Disposable) {
    enabledInTests = true
    Disposer.register(parentDisposable, Disposable { enabledInTests = false })
  }
}
