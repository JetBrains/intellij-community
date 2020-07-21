package org.jetbrains.plugins.feature.suggester

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ide.IdeTooltipManager
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.LightweightHint
import java.awt.Point

interface SuggestionPresenter {
    fun showSuggestion(project: Project, message: String)
}

class HintSuggestionPresenter : SuggestionPresenter {
    override fun showSuggestion(project: Project, message: String) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val hint = createHint(message)
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

class NotificationSuggestionPresenter : SuggestionPresenter {
    private val notificationGroup =
        NotificationGroup("IDE Feature Suggester", NotificationDisplayType.STICKY_BALLOON, false)

    override fun showSuggestion(project: Project, message: String) {
        val notification = notificationGroup.createNotification(
            title = "IDE Feature Suggester",
            subtitle = "Suggestion found!",
            content = message,
            type = NotificationType.INFORMATION
        )
        notification.notify(project)
    }
}