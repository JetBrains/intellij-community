// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsContexts.NotificationTitle
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.GitActionIdsHolder.Id.GPG_AGENT_CONFIGURATION_SUCCESS_MANUAL
import git4idea.GitNotificationIdsHolder.Companion.GPG_AGENT_CONFIGURATION_ERROR
import git4idea.GitNotificationIdsHolder.Companion.GPG_AGENT_CONFIGURATION_SUCCESS
import git4idea.i18n.GitBundle
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
internal class GpgAgentConfigurationNotificator(private val project: Project) {

  fun notifyConfigurationSuccessful(paths: GpgAgentPaths) {
    val message: String
    if (paths.gpgAgentConfBackup.exists()) {
      message = GitBundle.message("pgp.pinentry.configured.successfully.backup.message", paths.gpgAgentConfBackup)
    }
    else {
      message = GitBundle.message("pgp.pinentry.configured.successfully.message", paths.gpgAgentConf)
    }
    var displayId = GPG_AGENT_CONFIGURATION_SUCCESS
    GpgAgentConfiguratorNotification(displayId,
                                     GitBundle.message("pgp.pinentry.configured.successfully.title"),
                                     message,
                                     NotificationType.INFORMATION).apply {
      addAction(NotificationAction.createSimple(GitBundle.message("gpg.error.see.documentation.link.text"),
                                                GPG_AGENT_CONFIGURATION_SUCCESS_MANUAL.id) {
        HelpManager.getInstance().invokeHelp(GitBundle.message("gpg.pinentry.jb.manual.link"))
      })
    }.notifyExpirePrevious(project)
  }

  fun notifyConfigurationFailed(exception: Throwable) {
    val exceptionMessage = exception.message ?: return
    var message = HtmlBuilder().append(HtmlChunk.text(exceptionMessage)).toString()
    GpgAgentConfiguratorNotification(GPG_AGENT_CONFIGURATION_ERROR,
                                     GitBundle.message("pgp.pinentry.configuration.error.title"),
                                     message,
                                     NotificationType.ERROR)
      .notifyExpirePrevious(project)
  }

  private inner class GpgAgentConfiguratorNotification(
    displayId: String,
    title: @NotificationTitle String,
    content: @NotificationContent String,
    type: NotificationType = NotificationType.INFORMATION,
  ) : Notification(VcsNotifier.importantNotification().displayId, title, content, type) {

    init {
      setDisplayId(displayId)
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
