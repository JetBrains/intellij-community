// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.runOnceForApp
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsApplicationSettings
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.i18n.GitBundle

class GitModalCommitDeprecationNotifier : ProjectActivity {
  override suspend fun execute(project: Project) {
    val removedIn = Registry.stringValue("git.modal.commit.deprecation.removed.in")
    if (removedIn.isBlank()) return

    runOnceForApp("git.modal.commit.deprecation.notification") {
      if (!VcsApplicationSettings.getInstance().COMMIT_FROM_LOCAL_CHANGES) {
        val message = GitBundle.message("modal.commit.deprecation.notification.description", ApplicationNamesInfo.getInstance().fullProductName, removedIn)

        val notification = VcsNotifier.importantNotification()
          .createNotification(message, NotificationType.WARNING)
          .addAction(NotificationAction.createSimple(IdeBundle.message("link.learn.more")) {
            BrowserUtil.browse("https://youtrack.jetbrains.com/issue/IJPL-177161")
          })

        VcsNotifier.getInstance(project).notify(notification)
      }
    }
  }
}