// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.featuresSuggester.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import training.featuresSuggester.DocumentationSuggestion
import training.featuresSuggester.FeatureSuggesterBundle
import training.featuresSuggester.PopupSuggestion
import training.featuresSuggester.TipSuggestion
import training.featuresSuggester.settings.FeatureSuggesterSettings
import training.featuresSuggester.statistics.FeatureSuggesterStatistics

internal interface SuggestionPresenter {
  fun showSuggestion(project: Project, suggestion: PopupSuggestion, coroutineScope: CoroutineScope)
}

@Suppress("DialogTitleCapitalization")
internal class NotificationSuggestionPresenter : SuggestionPresenter {
  private val notificationGroup: NotificationGroup = NotificationGroupManager.getInstance()
    .getNotificationGroup("IDE Feature Suggester")

  override fun showSuggestion(project: Project, suggestion: PopupSuggestion, coroutineScope: CoroutineScope) {
    val notification = notificationGroup.createNotification(
      title = FeatureSuggesterBundle.message("notification.title"),
      content = suggestion.message,
      type = NotificationType.INFORMATION
    )

    val expireJob = coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement(), start = CoroutineStart.LAZY) {
      if (!notification.isExpired) {
        delay(10_000)
        notification.expire()
      }
    }

    when (suggestion) {
      is TipSuggestion -> {
        val action = createShowTipAction(project, notification, suggestion, coroutineScope, expireJob)
        if (action != null) {
          notification.addAction(action)
        }
      }
      is DocumentationSuggestion -> {
        notification.addAction(createGoToDocumentationAction(notification, suggestion, expireJob))
      }
    }
    notification.addAction(createDontSuggestAction(notification, suggestion, expireJob))

    notification.notify(project)
    expireJob.start()
    FeatureSuggesterStatistics.logNotificationShowed(suggestion.suggesterId)
  }

  private fun createDontSuggestAction(notification: Notification, suggestion: PopupSuggestion, expireJob: Job): AnAction {
    return object : AnAction(FeatureSuggesterBundle.message("notification.dont.suggest")) {
      override fun actionPerformed(e: AnActionEvent) {
        expireJob.cancel()
        val settings = FeatureSuggesterSettings.instance()
        settings.setEnabled(suggesterId = suggestion.suggesterId, enabled = false)
        notification.hideBalloon()
        FeatureSuggesterStatistics.logNotificationDontSuggest(suggestion.suggesterId)
      }
    }
  }

  private fun createGoToDocumentationAction(notification: Notification, suggestion: DocumentationSuggestion, expireJob: Job): AnAction {
    return object : AnAction(
      FeatureSuggesterBundle.message(
        "notification.open.help",
        ApplicationNamesInfo.getInstance().productName
      )
    ) {
      override fun actionPerformed(e: AnActionEvent) {
        expireJob.cancel()
        BrowserUtil.open(suggestion.documentURL)
        notification.hideBalloon()
        FeatureSuggesterStatistics.logNotificationLearnMore(suggestion.suggesterId)
      }
    }
  }

  private fun createShowTipAction(
    project: Project,
    notification: Notification,
    suggestion: TipSuggestion,
    coroutineScope: CoroutineScope,
    expireJob: Job,
  ): AnAction? {
    val tip = TipAndTrickBean.findById(suggestion.suggestingTipId) ?: return null
    return object : AnAction(FeatureSuggesterBundle.message("notification.learn.more")) {
      override fun actionPerformed(e: AnActionEvent) {
        expireJob.cancel()
        notification.hideBalloon()
        FeatureSuggesterStatistics.logNotificationLearnMore(suggestion.suggesterId)

        coroutineScope.launch {
          serviceAsync<TipAndTrickManager>().showTipDialog(project, tip)
        }
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }
  }
}
