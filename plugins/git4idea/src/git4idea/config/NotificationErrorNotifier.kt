// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
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

  private fun createNotification(text: String, description: String?): BadGitExecutableNotification {
    return BadGitExecutableNotification(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.displayId, null,
                                        getErrorTitle(text, description), null, getErrorMessage(text, description),
                                        NotificationType.ERROR, NotificationListener.UrlOpeningListener(false))
  }

  override fun showError(@Nls(capitalization = Nls.Capitalization.Sentence) text: String) {
    GitExecutableProblemsNotifier.notify(project, createNotification(text, null))
  }

  override fun executeTask(@Nls(capitalization = Nls.Capitalization.Title) title: String, cancellable: Boolean, action: () -> Unit) {
    ProgressManager.getInstance().run(object: Task.Backgroundable(project, title, cancellable) {
      override fun run(indicator: ProgressIndicator) {
        action()
      }
    })
  }

  override fun changeProgressTitle(@Nls(capitalization = Nls.Capitalization.Title) text: String) {
    ProgressManager.getInstance().progressIndicator?.text = text
  }

  override fun showMessage(@Nls(capitalization = Nls.Capitalization.Sentence) text: String) {
    VcsNotifier.getInstance(project).notifyInfo(text)
  }

  override fun hideProgress() {
  }
}
