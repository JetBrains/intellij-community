package org.jetbrains.plugins.feature.suggester.ui

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ide.IdeTooltipManager
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.LightweightHint
import org.jetbrains.plugins.feature.suggester.PopupSuggestion
import org.jetbrains.plugins.feature.suggester.settings.FeatureSuggesterSettings
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggestersStatisticsCollector
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggestersStatisticsCollector.Companion.NOTIFICATION_DONT_SUGGEST_EVENT_ID
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggestersStatisticsCollector.Companion.NOTIFICATION_LEARN_MORE_EVENT_ID
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggestersStatisticsCollector.Companion.NOTIFICATION_SHOWED_EVENT_ID
import org.jetbrains.plugins.feature.suggester.statistics.FeatureSuggestersStatisticsCollector.Companion.NOTIFICATION_THANKS_EVENT_ID
import java.awt.Point

interface SuggestionPresenter {
    fun showSuggestion(project: Project, suggestion: PopupSuggestion)
}

class HintSuggestionPresenter : SuggestionPresenter {
    override fun showSuggestion(project: Project, suggestion: PopupSuggestion) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val hint = createHint(suggestion.message)
        val hintManager = HintManager.getInstance() as HintManagerImpl
        val point: Point = hintManager.getHintPosition(hint, editor, HintManager.ABOVE)
        IdeTooltipManager.getInstance().hideCurrentNow(false)
        hintManager.showEditorHint(hint, editor, point, HintManager.HIDE_BY_ESCAPE, 0, false)
    }

    private fun createHint(message: String): LightweightHint {
        val label = HintUtil.createQuestionLabel(message)
        return LightweightHint(label)
    }
}

@Suppress("UnstableApiUsage")
class NotificationSuggestionPresenter :
    SuggestionPresenter {
    private val notificationGroup =
        NotificationGroup("IDE Feature Suggester", NotificationDisplayType.STICKY_BALLOON, false)
    private val statisticsCollector = FeatureSuggestersStatisticsCollector()

    override fun showSuggestion(project: Project, suggestion: PopupSuggestion) {
        val notification = notificationGroup.createNotification(
            title = "IDE Feature Suggester",
            subtitle = "Suggestion found!",
            content = suggestion.message,
            type = NotificationType.INFORMATION
        )
        notification.addAction(object : AnAction("Do not suggest this action anymore") {
            override fun actionPerformed(e: AnActionEvent) {
                val settings = FeatureSuggesterSettings.instance()
                settings.disableSuggester(suggestion.suggesterId)
                notification.hideBalloon()
                statisticsCollector.sendStatistics(NOTIFICATION_DONT_SUGGEST_EVENT_ID, suggestion.suggesterId)
            }
        })

        notification.addAction(object : AnAction("Thanks! Useful suggestion") {
            override fun actionPerformed(e: AnActionEvent) {
                notification.hideBalloon()
                statisticsCollector.sendStatistics(NOTIFICATION_THANKS_EVENT_ID, suggestion.suggesterId)
            }
        })

        val tip = getTipByFilename(suggestion.suggestingTipFilename)
        if (tip != null) {
            notification.addAction(object : AnAction("Learn more") {
                override fun actionPerformed(e: AnActionEvent) {
                    SingleTipDialog.showForProject(project, tip)
                    notification.hideBalloon()
                    statisticsCollector.sendStatistics(NOTIFICATION_LEARN_MORE_EVENT_ID, suggestion.suggesterId)
                }
            })
        }

        notification.notify(project)
        statisticsCollector.sendStatistics(NOTIFICATION_SHOWED_EVENT_ID, suggestion.suggesterId)
    }

    private fun getTipByFilename(tipFilename: String): TipAndTrickBean? {
        return TipAndTrickBean.EP_NAME.extensions.find { it.fileName == tipFilename }
    }
}