// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.squash

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.GitDisposable
import git4idea.inMemory.rebase.log.InMemoryRebaseOperations
import git4idea.inMemory.rebase.log.RebaseEntriesSource
import git4idea.log.createLogDataIn
import git4idea.log.refreshAndWait
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoPossibility
import git4idea.rebase.log.GitCommitEditingOperationResult.Complete.UndoResult
import git4idea.test.GitSingleRepoContext
import git4idea.test.assertCommitted
import git4idea.test.assertLastMessage
import git4idea.test.assertMessage
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.message
import git4idea.test.runUnderProgress
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@TestApplication
@ParameterizedClass(name = "{0}")
@MethodSource("squashOperations")
@Suppress("unused")
internal class GitSquashOperationTestBase private constructor(private val testType: String, private val operation: SquashTestOperation) {
  companion object {
    @JvmStatic
    fun squashOperations(): Stream<Arguments> =
      Stream.of(
        Arguments.of("Git Squash", GitSquashTestOperation()),
        Arguments.of("In-Memory Squash", InMemorySquashTestOperation())
      )
  }

  private abstract class SquashTestOperation {
    abstract fun execute(context: GitSingleRepoContext, commitsToSquash: List<VcsCommitMetadata>, newMessage: String): GitCommitEditingOperationResult
  }

  private class GitSquashTestOperation : SquashTestOperation() {
    override fun execute(context: GitSingleRepoContext, commitsToSquash: List<VcsCommitMetadata>, newMessage: String): GitCommitEditingOperationResult {
      return runUnderProgress { GitSquashOperation(context.repo).execute(commitsToSquash, newMessage) }
    }
  }

  private class InMemorySquashTestOperation : SquashTestOperation() {
    override fun execute(context: GitSingleRepoContext, commitsToSquash: List<VcsCommitMetadata>, newMessage: String): GitCommitEditingOperationResult {
      return runBlocking {
        val testCs = GitDisposable.getInstance(context.project).coroutineScope
        val logData = createLogDataIn(testCs, context.repo, context.logProvider)
        logData.refreshAndWait(context.repo, true)
        InMemoryRebaseOperations.squash(context.repo, commitsToSquash, newMessage, RebaseEntriesSource.LogData(logData))
      }
    }
  }

  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test squash last few commits`(): Unit = with(context) {
    val commitA = file("a").create().addCommit("Commit a").details()
    val commitB = file("b").create().addCommit("Commit b").details()
    val commitC = file("c").create().addCommit("Commit c").details()
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"

    operation.execute(context, commitsToSquash, newMessage)

    assertLastMessage(newMessage)
    repo.assertCommitted {
      added("a")
      added("b")
      added("c")
    }
  }

  @Test
  fun `test squash few non-last commits`(): Unit = with(context) {
    file("before").create().addCommit("Commit before")
    val commitA = file("a").create().addCommit("Commit a").details()
    val commitB = file("b").create().addCommit("Commit b").details()
    val commitC = file("c").create().addCommit("Commit c").details()
    file("after").create().addCommit("Commit after")
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"
    operation.execute(context, commitsToSquash, newMessage)

    assertMessage(newMessage, repo.message("HEAD^"))
    repo.assertCommitted(1) {
      added("after")
    }
    repo.assertCommitted(2) {
      added("a")
      added("b")
      added("c")
    }
    repo.assertCommitted(3) {
      added("before")
    }
  }

  @Test
  fun `test squash non-linear history`(): Unit = with(context) {
    val commitA = file("a").create().addCommit("Commit a").details()
    file("between1").create().addCommit("Commit between1")
    val commitB = file("b").create().addCommit("Commit b").details()
    file("between2").create().addCommit("Commit between2")
    val commitC = file("c").create().addCommit("Commit c").details()
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"
    operation.execute(context, commitsToSquash, newMessage)

    assertMessage(newMessage, repo.message("HEAD~2"))
    repo.assertCommitted(1) {
      added("between2")
    }
    repo.assertCommitted(2) {
      added("between1")
    }
    repo.assertCommitted(3) {
      added("a")
      added("b")
      added("c")
    }
  }

  @Test
  fun `test undo squash non-linear history`(): Unit = with(context) {
    val commitA = file("a").create().addCommit("Commit a").details()
    file("between1").create().addCommit("Commit between1")
    val commitB = file("b").create().addCommit("Commit b").details()
    file("between2").create().addCommit("Commit between2")
    val commitC = file("c").create().addCommit("Commit c").details()
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"
    val operationResult = operation.execute(context, commitsToSquash, newMessage) as Complete

    assertThat(runBlocking { operationResult.checkUndoPossibility() }).isInstanceOf(UndoPossibility.Possible::class.java)
    assertThat(operationResult.undo()).isInstanceOf(UndoResult.Success::class.java)

    repo.assertCommitted(1) {
      added("c")
    }
    repo.assertCommitted(2) {
      added("between2")
    }
    repo.assertCommitted(3) {
      added("b")
    }
    repo.assertCommitted(5) {
      added("a")
    }
  }

  @Test
  fun `test undo squash non-linear history is not allowed if repository changed`(): Unit = with(context) {
    val commitA = file("a").create().addCommit("Commit a").details()
    file("between1").create().addCommit("Commit between1")
    val commitB = file("b").create().addCommit("Commit b").details()
    file("between2").create().addCommit("Commit between2")
    val commitC = file("c").create().addCommit("Commit c").details()
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"
    val operationResult = operation.execute(context, commitsToSquash, newMessage) as Complete

    file("new").create().addCommit("new")

    assertThat(runBlocking { operationResult.checkUndoPossibility() })
      .isInstanceOf(UndoPossibility.Impossible.HeadMoved::class.java)
  }

  @Test
  fun `test undo squash linear history is not allowed if first changed commit is pushed to protected branch`(): Unit = with(context) {
    val commitA = file("a").create().addCommit("Commit a").details()
    file("between1").create().addCommit("Commit between1")
    val commitB = file("b").create().addCommit("Commit b").details()
    file("between2").create().addCommit("Commit between2")
    val commitC = file("c").create().addCommit("Commit c").details()
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"
    val operationResult = operation.execute(context, commitsToSquash, newMessage) as Complete

    git("update-ref refs/remotes/origin/master HEAD~2")

    assertThat(runBlocking { operationResult.checkUndoPossibility() })
      .isInstanceOf(UndoPossibility.Impossible.PushedToProtectedBranch::class.java)
  }
}
