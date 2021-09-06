package org.jetbrains.plugins.feature.suggester.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.feature.suggester.DocumentationSuggestion
import org.jetbrains.plugins.feature.suggester.PopupSuggestion
import org.jetbrains.plugins.feature.suggester.TipSuggestion
import org.jetbrains.plugins.feature.suggester.settings.FeatureSuggesterSettings
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggesterStatistics
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggesterStatistics.Companion.NOTIFICATION_DONT_SUGGEST_EVENT_ID
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggesterStatistics.Companion.NOTIFICATION_LEARN_MORE_EVENT_ID
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggesterStatistics.Companion.NOTIFICATION_SHOWED_EVENT_ID
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggesterStatistics.Companion.NOTIFICATION_THANKS_EVENT_ID

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
            title = "IDE Feature Suggester",
            content = suggestion.message,
            type = NotificationType.INFORMATION
        ).apply {
            addAction(createDontSuggestAction(this, suggestion))
            addAction(createThanksAction(this, suggestion))
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
        }

        notification.notify(project)
        FeatureSuggesterStatistics.sendStatistics(NOTIFICATION_SHOWED_EVENT_ID, suggestion.suggesterId)
    }

    private fun createDontSuggestAction(notification: Notification, suggestion: PopupSuggestion): AnAction {
        return object : AnAction("Do not suggest this action anymore") {
            override fun actionPerformed(e: AnActionEvent) {
                val settings = FeatureSuggesterSettings.instance()
                settings.setEnabled(suggestion.suggesterId, false)
                notification.hideBalloon()
                FeatureSuggesterStatistics.sendStatistics(NOTIFICATION_DONT_SUGGEST_EVENT_ID, suggestion.suggesterId)
            }
        }
    }

    private fun createThanksAction(notification: Notification, suggestion: PopupSuggestion): AnAction {
        return object : AnAction("Thanks! Useful suggestion") {
            override fun actionPerformed(e: AnActionEvent) {
                notification.hideBalloon()
                FeatureSuggesterStatistics.sendStatistics(NOTIFICATION_THANKS_EVENT_ID, suggestion.suggesterId)
            }
        }
    }

    private fun createGoToDocumentationAction(
        notification: Notification,
        suggestion: DocumentationSuggestion
    ): AnAction {
        return object : AnAction("Learn more") {
            override fun actionPerformed(e: AnActionEvent) {
                BrowserUtil.open(suggestion.documentURL)
                notification.hideBalloon()
                FeatureSuggesterStatistics.sendStatistics(NOTIFICATION_LEARN_MORE_EVENT_ID, suggestion.suggesterId)
            }
        }
    }

    private fun createShowTipAction(
        project: Project,
        notification: Notification,
        suggestion: TipSuggestion
    ): AnAction? {
        val tip = getTipByFilename(suggestion.suggestingTipFilename) ?: return null
        return object : AnAction("Learn more") {
            override fun actionPerformed(e: AnActionEvent) {
                SingleTipDialog.showForProject(project, tip)
                notification.hideBalloon()
                FeatureSuggesterStatistics.sendStatistics(NOTIFICATION_LEARN_MORE_EVENT_ID, suggestion.suggesterId)
            }
        }
    }

    private fun getTipByFilename(tipFilename: String): TipAndTrickBean? {
        return TipAndTrickBean.EP_NAME.extensions.find { it.fileName == tipFilename }
    }
}
