// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.ide.util.RunOnceUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.actions.GitUnshallowRepositoryAction
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle

internal class GitShallowRepositoryCheck() : ProjectActivity {
  override suspend fun execute(project: Project) {
    val repository = GitRepositoryManager.getInstance(project).repositories.singleOrNull() ?: return

    RunOnceUtil.runOnceForProject(project, ID) {
      suggestToUnshallow(repository, project)
    }
  }

  private fun suggestToUnshallow(repository: GitRepository, project: Project) {
    if (repository.info.isShallow) {
      val fetcher = GitFetchSupport.fetchSupport(project)
      val remote = fetcher.getDefaultRemoteToFetch(repository)
      if (remote == null) {
        LOG.debug("Couldn't detect remote for shallow repository")
        return
      }

      VcsNotifier.getInstance(project).notify(
        VcsNotifier.importantNotification().createNotification(
          GitBundle.message("unshallow.repository.notification.message"),
          GitBundle.message("unshallow.repository.notification.title"),
          NotificationType.INFORMATION,
        ).addAction(
          NotificationAction.createExpiring(GitBundle.message("action.Git.Unshallow.text")) { e, _ ->
            GitUnshallowRepositoryAction().actionPerformed(e)
          }
        )
      )
    }
  }
}

private val LOG = Logger.getInstance(GitShallowRepositoryCheck::class.java)

private const val ID = "git.unshallow"