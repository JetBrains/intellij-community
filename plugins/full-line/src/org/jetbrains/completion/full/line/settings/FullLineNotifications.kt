package org.jetbrains.completion.full.line.settings

import com.intellij.lang.Language
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.completion.full.line.settings.MLServerCompletionBundle.Companion.message
import org.jetbrains.completion.full.line.tasks.SetupLocalModelsTask

object FullLineNotifications {
  // Required using deprecated `NotificationGroup#balloonGroup` due to 201-202 IDEA support,
  // bcs in old versions `NotificationGroup` not yet implemented
  @Suppress("DEPRECATION")
  private val group = NotificationGroup.balloonGroup("Full Line Completion Plugin")

  object Cloud {
    private var authKeyErrorNotified: Boolean = false

    @Synchronized
    fun showAuthorizationError(project: Project) {
      if (authKeyErrorNotified) return

      project.showBalloon(
        message("full.line.notifications.auth.title"),
        message("full.line.notifications.auth.text"),
        NotificationType.WARNING,
        action(message("full.line.notifications.auth.button")) { _, notification ->
          ShowSettingsUtil.getInstance().showSettingsDialog(project, ApplicationBundle.message("title.code.completion"))
          notification.hideBalloon()
          authKeyErrorNotified = false
        }
      )
      authKeyErrorNotified = true
    }

    fun clearAuthError() {
      authKeyErrorNotified = false
    }
  }

  object Local {
    private val missingModelNotified: MutableSet<String> = mutableSetOf()
    private val availableModelUpdateNotified: MutableSet<String> = mutableSetOf()
    private val corruptedModelNotified: MutableSet<String> = mutableSetOf()

    @Synchronized
    fun showAvailableModelUpdate(project: Project, language: Language) = showLanguageModelAction(
      project, language, NotificationType.INFORMATION, availableModelUpdateNotified,
      message("full.line.notifications.local.model.available.title"),
      message("full.line.notifications.local.model.available.text", language.displayName),
      message("full.line.notifications.local.model.available.button"),
      SetupLocalModelsTask.Action.UPDATE
    )

    @Synchronized
    fun showMissingModel(project: Project, language: Language) = showLanguageModelAction(
      project, language, NotificationType.INFORMATION, missingModelNotified,
      message("full.line.notifications.local.model.missing.title"),
      message("full.line.notifications.local.model.missing.text", language.displayName),
      message("full.line.notifications.local.model.missing.button"),
      SetupLocalModelsTask.Action.DOWNLOAD
    )

    @Synchronized
    fun showCorruptedModel(project: Project, language: Language) = showLanguageModelAction(
      project, language, NotificationType.WARNING, corruptedModelNotified,
      message("full.line.notifications.local.model.corrupted.title"),
      message("full.line.notifications.local.model.corrupted.text"),
      message("full.line.notifications.local.model.corrupted.button"),
      SetupLocalModelsTask.Action.REMOVE
    )

    private fun showLanguageModelAction(
      project: Project,
      language: Language,
      type: NotificationType,
      notified: MutableSet<String>,
      @NlsContexts.NotificationTitle title: String,
      @NlsContexts.NotificationContent text: String,
      @NlsContexts.NotificationContent button: String,
      action: SetupLocalModelsTask.Action
    ) {
      if (notified.contains(language.id)) return

      project.showBalloon(title, text, type, action(button) { _, notification ->
        SetupLocalModelsTask(project, SetupLocalModelsTask.ToDoParams(language, action)).queue()
        notified.remove(language.id)

        notification.hideBalloon()
      })
      notified.add(language.id)
    }
  }

  private fun Project.showBalloon(@NlsContexts.NotificationTitle title: String, @NlsContexts.NotificationContent content: String, type: NotificationType, vararg actions: AnAction): Notification {
    return group.createNotification(title, content, type).also {
      if (actions.isNotEmpty()) it.addActions(actions.toList() as Collection<AnAction>)
      it.notify(this)
    }
  }

  private fun action(@NlsContexts.NotificationContent label: String, run: (AnActionEvent?, Notification) -> Unit): NotificationAction {
    return object : NotificationAction(label) {
      override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        run(e, notification)
      }
    }
  }
}
