// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.fetch

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.GitActionIdsHolder
import git4idea.GitNotificationIdsHolder
import git4idea.config.GitIncomingRemoteCheckStrategy
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls

@Service(Service.Level.PROJECT)
internal class GitAutoFetchNotificationsService(private val project: Project) {
  fun shouldShow(): Boolean {
    val settings = GitVcsSettings.getInstance(project)
    val isAutoFetchDisabled = settings.incomingCommitsCheckStrategy != GitIncomingRemoteCheckStrategy.FETCH
    val shownCount = PropertiesComponent.getInstance(project).getInt(SUGGESTION_SHOWN_COUNTER, 0)
    return isAutoFetchDisabled && shownCount < MAX_SUGGESTION_SHOW_COUNT
  }

  fun getSuggestionMessage(): @Nls String = GitBundle.message("auto.fetch.notification.suggestion.message")

  fun createActions(): List<NotificationAction> {
    incrementCounter()
    val enableAction = NotificationAction.createSimpleExpiring(GitBundle.message("auto.fetch.notification.action.enable")) {
      GitVcsSettings.getInstance(project).setAutoFetch(true)
      showAutoFetchEnabledNotification()
    }
    val doNotAskAction = NotificationAction.createSimpleExpiring(GitBundle.message("auto.fetch.notification.action.do.not.ask.again")) {
      disableSuggestion()
    }
    return listOf(enableAction, doNotAskAction)
  }

  private fun showAutoFetchEnabledNotification() {
    VcsNotifier.getInstance(project).notifyInfo(
      GitNotificationIdsHolder.AUTOFETCH_ENABLED,
      GitBundle.message("auto.fetch.notification.enabled.title"),
      GitBundle.message("auto.fetch.notification.enabled.message"),
    )
  }

  private fun incrementCounter() {
    val props = PropertiesComponent.getInstance(project)
    val currentCount = props.getInt(SUGGESTION_SHOWN_COUNTER, 0)
    props.setValue(SUGGESTION_SHOWN_COUNTER, currentCount + 1, 0)
  }

  private fun disableSuggestion() {
    PropertiesComponent.getInstance(project).setValue(SUGGESTION_SHOWN_COUNTER, MAX_SUGGESTION_SHOW_COUNT, 0)
  }

  companion object {
    private const val SUGGESTION_SHOWN_COUNTER = "git.auto.fetch.suggestion.counter"
    private const val MAX_SUGGESTION_SHOW_COUNT = 3
  }
}
