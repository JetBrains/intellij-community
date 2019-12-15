// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.config.GitExecutableProblemsNotifier.BadGitExecutableNotification

internal class NotificationErrorNotifier(val project: Project) : ErrorNotifier {
  override fun showError(text: String, description: String?, fixOption: ErrorNotifier.FixOption) {
    val notification = createNotification(text, description)
    notification.addAction(NotificationAction.createSimpleExpiring(fixOption.text) {
      fixOption.fix()
    })
    GitExecutableProblemsNotifier.notify(project, notification)
  }

  private fun createNotification(text: String, description: String?): BadGitExecutableNotification {
    return BadGitExecutableNotification(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.displayId, null,
                                        getErrorTitle(text, description), null, getErrorMessage(text, description),
                                        NotificationType.ERROR, null)
  }

  override fun showError(text: String) {
    GitExecutableProblemsNotifier.notify(project, createNotification(text, null))
  }

  override fun executeTask(title: String, cancellable: Boolean, action: () -> Unit) {
    ProgressManager.getInstance().run(object: Task.Backgroundable(project, title, cancellable) {
      override fun run(indicator: ProgressIndicator) {
        action()
      }
    })
  }

  override fun changeProgressTitle(text: String) {
    ProgressManager.getInstance().progressIndicator?.text = text
  }

  override fun showMessage(text: String) {
    VcsNotifier.getInstance(project).notifyInfo(text)
  }

  override fun hideProgress() {
  }
}
