// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import git4idea.GitNotificationIdsHolder
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Background tasks that check or fetch the remote commits of a push.
 */
@Service(Service.Level.PROJECT)
internal class GitPushDivergenceTasks(private val project: Project, private val coroutineScope: CoroutineScope) {
  /**
   * Checks the default push of the repositories in the background, then calls [onChecked] with the result.
   * A canceled or failed check reports a divergence, so the push dialog opens and nothing is pushed without a check.
   */
  fun checkDefaultPushInBackground(repositories: List<GitRepository>, onChecked: (Boolean) -> Unit) {
    coroutineScope.launch {
      var diverged = true
      try {
        diverged = withBackgroundProgress(project, GitBundle.message("push.after.commit.checking.remote")) {
          GitPushDivergenceDetector.getInstance(project).hasDivergedDefaultPush(repositories)
        }
      }
      finally {
        onChecked(diverged)
      }
    }
  }

  /**
   * Pushes in the background and notifies about the result.
   * [operation] creates the push inside the task, because a push takes the progress indicator of the task when it is created.
   */
  fun pushInBackground(customParams: Map<String, VcsPushOptionValue>, operation: () -> GitPushOperation) {
    coroutineScope.launch {
      withBackgroundProgress(project, GitBundle.message("push.notification.force.push.progress.title.pushing")) {
        withContext(Dispatchers.IO) {
          coroutineToIndicator { GitPusher.pushAndNotify(project, operation(), customParams) }
        }
      }
    }
  }

  /**
   * Fetches the push targets in the background, then calls [onFetched] on the EDT. A failed fetch shows an error notification.
   */
  fun fetchInBackground(targets: Map<GitRepository, GitPushTarget>, onFetched: () -> Unit) {
    coroutineScope.launch {
      withBackgroundProgress(project, GitBundle.message("push.notification.fetch.progress")) {
        for ([repository, target] in targets) {
          val error = GitPushDivergenceDetector.getInstance(project).fetchTarget(repository, target) ?: continue
          VcsNotifier.getInstance(project).notifyError(GitNotificationIdsHolder.FETCH_ERROR, "", error.text)
        }
      }
      withContext(Dispatchers.EDT) { onFetched() }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GitPushDivergenceTasks = project.service()
  }
}
