// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.performanceTesting

import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScopeBlocking
import com.jetbrains.performancePlugin.PerformanceTestSpan
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.popup.GitBranchesTreePopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

class GitShowVcsWidgetCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "gitShowBranchWidget"
    const val PREFIX = CMD_PREFIX + NAME
  }

  @Suppress
  override suspend fun doExecute(context: PlaybackContext) {
    val repository = GitRepositoryManager.getInstance(context.project).repositories.single()
    withContext(Dispatchers.EDT) {
      val treePopup = (GitBranchesTreePopup.create(context.project, repository) as GitBranchesTreePopup)
      treePopup.showCenteredInCurrentWindow(context.project)
      PerformanceTestSpan.TRACER.spanBuilder(NAME).useWithScopeBlocking {
        treePopup.waitTreeExpand(3.minutes)
      }
    }
  }

  override fun getName(): String = NAME

}