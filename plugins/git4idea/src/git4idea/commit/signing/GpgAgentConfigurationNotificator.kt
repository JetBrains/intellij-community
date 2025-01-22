// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.ide.util.runOnceForProject
import com.intellij.idea.AppMode
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsContexts.NotificationTitle
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.GitActionIdsHolder.Id.*
import git4idea.GitNotificationIdsHolder.Companion.GPG_AGENT_CONFIGURATION_ERROR
import git4idea.GitNotificationIdsHolder.Companion.GPG_AGENT_CONFIGURATION_PROPOSE
import git4idea.GitNotificationIdsHolder.Companion.GPG_AGENT_CONFIGURATION_PROPOSE_SUGGESTION
import git4idea.GitNotificationIdsHolder.Companion.GPG_AGENT_CONFIGURATION_SUCCESS
import git4idea.config.GitExecutable
import git4idea.config.GitExecutableManager
import git4idea.i18n.GitBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import kotlin.io.path.pathString

@Service(Service.Level.PROJECT)
internal class GpgAgentConfigurationNotificator(private val project: Project,
                                                private val coroutineScope: CoroutineScope) {

  private fun isEnabled() = Registry.`is`("git.commit.gpg.signing.enable.embedded.pinentry.notification.proposal", false)

  @RequiresBackgroundThread
  fun proposeCustomPinentryAgentConfiguration(isSuggestion: Boolean, type: NotificationType = NotificationType.INFORMATION) {
    if (!isEnabled()) return
    if (!project.service<GpgAgentConfigurator>().canBeConfigured(project)) return

    if (isSuggestion) {
      coroutineScope.launch {
        runOnceForProject(project, id = "GPG_PINENTRY_CONFIGURATION_PROPOSAL") {
          showPinentryConfigurationNotification(true, type)
        }
      }
    } else {
      showPinentryConfigurationNotification(false, type)
    }
  }

  private fun showPinentryConfigurationNotification(isSuggestion: Boolean, type: NotificationType) {
    val displayId = if (isSuggestion) GPG_AGENT_CONFIGURATION_PROPOSE else GPG_AGENT_CONFIGURATION_PROPOSE_SUGGESTION
    GpgAgentConfiguratorNotification(
      displayId = displayId,
      title = GitBundle.message("gpg.pinentry.configuration.proposal.title"),
      content = getConfigurationProposalMessage(),
      isSuggestion = isSuggestion,
      type = type,
    ).apply {
      val configureActionId =
        if (isSuggestion) GPG_AGENT_CONFIGURATION_PROPOSE_SUGGESTION_CONFIGURE.id else GPG_AGENT_CONFIGURATION_PROPOSE_CONFIGURE.id
      addAction(NotificationAction.createSimple(GitBundle.message("gpg.pinentry.configuration.proposal.configure"),
                                                configureActionId) {
        expire()
        project.service<GpgAgentConfigurator>().configure()
      })
      val manualActionId =
        if (isSuggestion) GPG_AGENT_CONFIGURATION_PROPOSE_SUGGESTION_MANUAL.id else GPG_AGENT_CONFIGURATION_PROPOSE_MANUAL.id
      addAction(NotificationAction.createSimple(GitBundle.message("gpg.error.see.documentation.link.text"),
                                                manualActionId) {
        HelpManager.getInstance().invokeHelp(GitBundle.message("gpg.pinentry.jb.manual.link"))
      })
    }.notifyExpirePrevious(project)
  }

  private fun getConfigurationProposalMessage(): @Nls String =
    GitBundle.message(
      "gpg.pinentry.configuration.proposal.message",
      when {
        AppMode.isRemoteDevHost() -> 0
        GitExecutableManager.getInstance().getExecutable(project) is GitExecutable.Wsl -> 1
        else -> 2
      },
      ApplicationNamesInfo.getInstance().fullProductName,
    )

  fun notifyConfigurationSuccessful(paths: GpgAgentPaths, backupCreated: Boolean) {
    var message = GitBundle.message(
      "gpg.pinentry.configured.successfully.message",
      FileUtil.getLocationRelativeToUserHome(paths.gpgAgentConf.pathString)
    )
    if (backupCreated) {
      message += "<br/><br/>" +
                 GitBundle.message("gpg.pinentry.configured.successfully.backup.message",
                                   FileUtil.getLocationRelativeToUserHome(paths.gpgAgentConfBackup.pathString))
    }
    GpgAgentConfiguratorNotification(GPG_AGENT_CONFIGURATION_SUCCESS,
                                     GitBundle.message("gpg.pinentry.configured.successfully.title"),
                                     message,
                                     NotificationType.INFORMATION).apply {
      addAction(NotificationAction.createSimple(GitBundle.message("gpg.error.see.documentation.link.text"),
                                                GPG_AGENT_CONFIGURATION_SUCCESS_MANUAL.id) {
        HelpManager.getInstance().invokeHelp(GitBundle.message("gpg.pinentry.jb.manual.link"))
      })
    }.notifyExpirePrevious(project)
  }

  fun notifyConfigurationFailed(message: @NotificationContent String) {
    GpgAgentConfiguratorNotification(GPG_AGENT_CONFIGURATION_ERROR,
                                     GitBundle.message("gpg.pinentry.configuration.error.title"),
                                     message,
                                     NotificationType.ERROR)
      .notifyExpirePrevious(project)
  }

  private inner class GpgAgentConfiguratorNotification(
    displayId: String,
    title: @NotificationTitle String,
    content: @NotificationContent String,
    type: NotificationType = NotificationType.INFORMATION,
    isSuggestion: Boolean = false,
  ) : Notification(VcsNotifier.importantNotification().displayId, title, content, type) {

    init {
      setDisplayId(displayId)
      setSuggestionType(isSuggestion)
      configureDoNotAskOption(displayId,
                              if (isSuggestion) GitBundle.message("gpg.pinentry.configuration.global.suggestion.do.not.ask.again.display.name")
                              else GitBundle.message("gpg.pinentry.configuration.suggestion.do.not.ask.again.display.name"))
    }

    fun notifyExpirePrevious(project: Project) {
      synchronized(this@GpgAgentConfigurationNotificator) {
        NotificationsManager.getNotificationsManager()
          .getNotificationsOfType(GpgAgentConfiguratorNotification::class.java, project)
          .filter { notification -> notification.displayId == this.displayId }
          .forEach(GpgAgentConfiguratorNotification::expire)
        notify(project)
      }
    }
  }
}
