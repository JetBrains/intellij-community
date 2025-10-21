// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.concurrency.waitForPromise
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vcs.impl.VcsInitialization
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.vcs.git.branch.popup.GitBranchesPopup
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import git4idea.repo.GitRepository
import git4idea.ui.branch.popup.GitBranchesTreePopupOnBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration.Companion.minutes

class GitShowVcsWidgetCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "gitShowBranchWidget"
    const val PREFIX = CMD_PREFIX + NAME
  }

  @TestOnly
  override suspend fun doExecute(context: PlaybackContext) {
    val repository = getGitRepository(context)

    withContext(Dispatchers.EDT) {
      val treePopup = (GitBranchesTreePopupOnBackend.create(context.project, repository) as GitBranchesPopup)
      treePopup.showCenteredInCurrentWindow(context.project)
      PerformanceTestSpan.TRACER.spanBuilder(NAME).use { span->
        treePopup.promiseExpandTree().waitForPromise(3.minutes)
        span.setAttribute("expandedPaths",treePopup.getExpandedPathsSize().toLong())
      }
    }
  }

  @TestOnly
  private fun getGitRepository(context: PlaybackContext): GitRepository {
    VcsInitialization.getInstance(context.project).waitFinished()
    val repositoryManager = VcsRepositoryManager.getInstance(context.project)
    return repositoryManager.getRepositoryForFile(context.project.guessProjectDir()) as GitRepository
  }

  override fun getName(): String = NAME

}