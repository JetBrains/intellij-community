// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.platform.vcs.impl.shared.commit.CommitToolWindowViewModel
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitDetails
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.vcs.commit.CommitToAmend
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.test.GitSingleRepoContext
import git4idea.test.file
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class GitChangesViewWorkflowManagerAmendMode {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @BeforeEach
  fun setUp(): Unit = with(context) {
    VcsProjectLog.ensureLogCreated(project)
  }

  @Test
  fun `test get edited commit details`(): Unit = with(context) {
    val initialMsg = ":initial:"
    val commit = file("a.txt").create().addCommit(initialMsg).hash()
    repo.update()

    val workflowManager = ChangesViewWorkflowManager.getInstance(project)
    toggleAmendCommitMode(workflowManager, true)

    timeoutRunBlocking(5.seconds) {
      val edited = workflowManager.editedCommit.filterIsInstance<EditedCommitDetails>().first()
      assertThat(edited.fullMessage).isEqualTo(initialMsg)
      assertThat(edited.commitHash.asString()).isEqualTo(commit)
    }

    toggleAmendCommitMode(workflowManager, false)

    timeoutRunBlocking(5.seconds) {
      workflowManager.editedCommit.first { it == null } // Await the commit details cleanup.
    }
  }

  @Test
  fun `test get edited commit details from view model`(): Unit = with(context) {
    val initialMsg = ":initial:"
    val commit = file("a.txt").create().addCommit(initialMsg).hash()
    repo.update()

    val workflowManager = ChangesViewWorkflowManager.getInstance(project)
    val viewModel = project.service<CommitToolWindowViewModel>()
    toggleAmendCommitMode(workflowManager, true)

    timeoutRunBlocking(5.seconds) {
      val edited = viewModel.editedCommit.filterIsInstance<EditedCommitDetails>().first()
      assertThat(edited.fullMessage).isEqualTo(initialMsg)
      assertThat(edited.commitHash.asString()).isEqualTo(commit)
    }

    toggleAmendCommitMode(workflowManager, false)

    timeoutRunBlocking(5.seconds) {
      viewModel.editedCommit.first { it == null } // Await the commit details cleanup.
    }
  }

  private fun toggleAmendCommitMode(workflowManager: ChangesViewWorkflowManager, value: Boolean) {
    runInEdtAndWait {
      workflowManager.commitWorkflowHandler!!.amendCommitHandler.commitToAmend = if (value) CommitToAmend.Last.Unknown else CommitToAmend.None
    }
  }
}