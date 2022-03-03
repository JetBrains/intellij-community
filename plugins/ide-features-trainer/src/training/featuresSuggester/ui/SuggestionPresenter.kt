package training.featuresSuggester.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import training.featuresSuggester.DocumentationSuggestion
import training.featuresSuggester.FeatureSuggesterBundle
import training.featuresSuggester.PopupSuggestion
import training.featuresSuggester.TipSuggestion
import training.featuresSuggester.settings.FeatureSuggesterSettings
import training.featuresSuggester.statistics.FeatureSuggesterStatistics

interface SuggestionPresenter {
  fun showSuggestion(project: Project, suggestion: PopupSuggestion)
}

@Suppress("UnstableApiUsage", "DialogTitleCapitalization")
class NotificationSuggestionPresenter :
  SuggestionPresenter {
  private val notificationGroup: NotificationGroup = NotificationGroupManager.getInstance()
    .getNotificationGroup("IDE Feature Suggester")

  override fun showSuggestion(project: Project, suggestion: PopupSuggestion) {
    val notification = notificationGroup.createNotification(
      title = FeatureSuggesterBundle.message("notification.title"),
      content = suggestion.message,
      type = NotificationType.INFORMATION
    ).apply {
      when (suggestion) {
        is TipSuggestion -> {
          val action = createShowTipAction(project, this, suggestion)
          if (action != null) {
            addAction(action)
          }
        }
        is DocumentationSuggestion -> {
          addAction(createGoToDocumentationAction(this, suggestion))
        }
      }
      addAction(createDontSuggestAction(this, suggestion))
    }

    notification.notify(project)
    FeatureSuggesterStatistics.logNotificationShowed(suggestion.suggesterId)
  }

  private fun createDontSuggestAction(notification: Notification, suggestion: PopupSuggestion): AnAction {
    return object : AnAction(FeatureSuggesterBundle.message("notification.dont.suggest")) {
      override fun actionPerformed(e: AnActionEvent) {
        val settings = FeatureSuggesterSettings.instance()
        settings.setEnabled(suggestion.suggesterId, false)
        notification.hideBalloon()
        FeatureSuggesterStatistics.logNotificationDontSuggest(suggestion.suggesterId)
      }
    }
  }

  private fun createGoToDocumentationAction(
    notification: Notification,
    suggestion: DocumentationSuggestion
  ): AnAction {
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

  private fun createShowTipAction(
    project: Project,
    notification: Notification,
    suggestion: TipSuggestion
  ): AnAction? {
    val tip = getTipByFilename(suggestion.suggestingTipFilename) ?: return null
    return object : AnAction(FeatureSuggesterBundle.message("notification.learn.more")) {
      override fun actionPerformed(e: AnActionEvent) {
        SingleTipDialog.showForProject(project, tip)
        notification.hideBalloon()
        FeatureSuggesterStatistics.logNotificationLearnMore(suggestion.suggesterId)
      }
    }
  }

  private fun getTipByFilename(tipFilename: String): TipAndTrickBean? {
    return TipAndTrickBean.EP_NAME.extensions.find { it.fileName == tipFilename }
  }
}
