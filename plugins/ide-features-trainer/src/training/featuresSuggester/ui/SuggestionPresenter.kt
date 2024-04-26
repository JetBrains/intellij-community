// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.featuresSuggester.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import kotlinx.coroutines.launch
import training.featuresSuggester.DocumentationSuggestion
import training.featuresSuggester.FeatureSuggesterBundle
import training.featuresSuggester.PopupSuggestion
import training.featuresSuggester.TipSuggestion
import training.featuresSuggester.settings.FeatureSuggesterSettings
import training.featuresSuggester.statistics.FeatureSuggesterStatistics

internal interface SuggestionPresenter {
  fun showSuggestion(project: Project, suggestion: PopupSuggestion, disposable: Disposable)
}

@Suppress("DialogTitleCapitalization")
internal class NotificationSuggestionPresenter : SuggestionPresenter {
  private val notificationGroup: NotificationGroup = NotificationGroupManager.getInstance()
    .getNotificationGroup("IDE Feature Suggester")

  override fun showSuggestion(project: Project, suggestion: PopupSuggestion, disposable: Disposable) {
    val notification = notificationGroup.createNotification(
      title = FeatureSuggesterBundle.message("notification.title"),
      content = suggestion.message,
      type = NotificationType.INFORMATION
    )

    when (suggestion) {
      is TipSuggestion -> {
        val action = createShowTipAction(project, notification, suggestion)
        if (action != null) {
          notification.addAction(action)
        }
      }
      is DocumentationSuggestion -> {
        notification.addAction(createGoToDocumentationAction(notification, suggestion))
      }
    }
    notification.addAction(createDontSuggestAction(notification, suggestion))

    notification.notify(project)
    Alarm(disposable).addRequest(notification::expire, 10000, ModalityState.any())
    FeatureSuggesterStatistics.logNotificationShowed(suggestion.suggesterId)
  }

  private fun createDontSuggestAction(notification: Notification, suggestion: PopupSuggestion): AnAction {
    return object : AnAction(FeatureSuggesterBundle.message("notification.dont.suggest")) {
      override fun actionPerformed(e: AnActionEvent) {
        val settings = FeatureSuggesterSettings.instance()
        settings.setEnabled(suggesterId = suggestion.suggesterId, enabled = false)
        notification.hideBalloon()
        FeatureSuggesterStatistics.logNotificationDontSuggest(suggestion.suggesterId)
      }
    }
  }

  private fun createGoToDocumentationAction(notification: Notification, suggestion: DocumentationSuggestion): AnAction {
    return object : AnAction(
      FeatureSuggesterBundle.message(
        "notification.open.help",
        ApplicationNamesInfo.getInstance().productName
      )
    ) {
      override fun actionPerformed(e: AnActionEvent) {
        BrowserUtil.open(suggestion.documentURL)
        notification.hideBalloon()
        FeatureSuggesterStatistics.logNotificationLearnMore(suggestion.suggesterId)
      }
    }
  }

  private fun createShowTipAction(project: Project, notification: Notification, suggestion: TipSuggestion): AnAction? {
    val tip = TipAndTrickBean.findById(suggestion.suggestingTipId) ?: return null
    return object : AnAction(FeatureSuggesterBundle.message("notification.learn.more")) {
      override fun actionPerformed(e: AnActionEvent) {
        (project as ComponentManagerEx).getCoroutineScope().launch {
          TipAndTrickManager.getInstance().showTipDialog(project, tip)
          notification.hideBalloon()
          FeatureSuggesterStatistics.logNotificationLearnMore(suggestion.suggesterId)
        }
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }
  }
}
