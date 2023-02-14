// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.config.GitExecutableProblemsNotifier.BadGitExecutableNotification
import org.jetbrains.annotations.Nls

internal class NotificationErrorNotifier(val project: Project) : ErrorNotifier {
  override fun showError(@Nls(capitalization = Nls.Capitalization.Sentence) text: String,
                         @Nls(capitalization = Nls.Capitalization.Sentence) description: String?,
                         fixOption: ErrorNotifier.FixOption?) {
    val notification = createNotification(text, description)
    if (fixOption != null) {
      notification.addAction(NotificationAction.createSimpleExpiring(fixOption.text) {
        fixOption.fix()
      })
    }
    GitExecutableProblemsNotifier.notify(project, notification)
  }

  private fun createNotification(text: @NlsContexts.NotificationTitle String, description: @NlsContexts.NotificationContent String?): BadGitExecutableNotification {
    val notification = BadGitExecutableNotification(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.displayId,
                                                    getErrorTitle(text, description),
                                                    getErrorMessage(text, description),
                                                    NotificationType.ERROR)
    notification.setListener(NotificationListener.UrlOpeningListener(false))
    return notification
  }

  override fun showError(@Nls(capitalization = Nls.Capitalization.Sentence) text: String) {
    GitExecutableProblemsNotifier.notify(project, createNotification(text, null))
  }

  override fun executeTask(@NlsContexts.ProgressTitle title: String, cancellable: Boolean, action: () -> Unit) {
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, cancellable) {
      override fun run(indicator: ProgressIndicator) {
        action()
      }
    })
  }

  override fun changeProgressTitle(@NlsContexts.ProgressTitle text: String) {
    ProgressManager.getInstance().progressIndicator?.text = text
  }

  override fun showMessage(@NlsContexts.NotificationContent message: String) {
    VcsNotifier.getInstance(project).notifyInfo(null, "", message)
  }

  override fun hideProgress() {
  }
}
