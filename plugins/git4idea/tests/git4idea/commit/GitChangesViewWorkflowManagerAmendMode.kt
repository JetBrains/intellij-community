// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.platform.vcs.impl.shared.commit.CommitToolWindowViewModel
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitDetails
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.test.GitSingleRepoTest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

internal class GitChangesViewWorkflowManagerAmendMode : GitSingleRepoTest() {
  override fun setUp() {
    super.setUp()
    VcsProjectLog.ensureLogCreated(project)
  }

  fun `test get edited commit details`() {
    val initialMsg = ":initial:"
    val commit = file("a.txt").create().addCommit(initialMsg).hash()
    repo.update()

    val workflowManager = ChangesViewWorkflowManager.getInstance(project)
    toggleAmendCommitMode(workflowManager, true)

    timeoutRunBlocking(5.seconds) {
      val edited = workflowManager.editedCommit.filterIsInstance<EditedCommitDetails>().first()
      assertEquals(initialMsg, edited.fullMessage)
      assertEquals(commit, edited.commitHash.asString())
    }

    toggleAmendCommitMode(workflowManager, false)

    timeoutRunBlocking(5.seconds) {
      workflowManager.editedCommit.filter { it == null }.first() // Awaiting the commit details to be cleared
    }
  }

  fun `test get edited commit details from view model`() {
    val initialMsg = ":initial:"
    val commit = file("a.txt").create().addCommit(initialMsg).hash()
    repo.update()

    val workflowManager = ChangesViewWorkflowManager.getInstance(project)
    val viewModel = project.service<CommitToolWindowViewModel>()
    toggleAmendCommitMode(workflowManager, true)

    timeoutRunBlocking(5.seconds) {
      val edited = viewModel.editedCommit.filterIsInstance<EditedCommitDetails>().first()
      assertEquals(initialMsg, edited.fullMessage)
      assertEquals(commit, edited.commitHash.asString())
    }

    toggleAmendCommitMode(workflowManager, false)

    timeoutRunBlocking(5.seconds) {
      viewModel.editedCommit.filter { it == null }.first() // Awaiting the commit details to be cleared
    }
  }

  private fun toggleAmendCommitMode(workflowManager: ChangesViewWorkflowManager, value: Boolean) {
    runInEdtAndWait {
      workflowManager.commitWorkflowHandler!!.amendCommitHandler.isAmendCommitMode = value
    }
  }
}