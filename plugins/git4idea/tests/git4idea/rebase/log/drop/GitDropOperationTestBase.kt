// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.drop

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
import git4idea.test.file
import git4idea.test.gitSingleRepoContextFixture
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
@MethodSource("dropOperations")
@Suppress("unused")
internal class GitDropOperationTestBase private constructor(private val testType: String, private val operation: DropTestOperation) {
  companion object {
    @JvmStatic
    fun dropOperations(): Stream<Arguments> =
      Stream.of(
        Arguments.of("Git Drop", GitDropTestOperation()),
        Arguments.of("In-Memory Drop", InMemoryDropTestOperation())
      )
  }

  private abstract class DropTestOperation {
    abstract fun execute(context: GitSingleRepoContext, commitsToDrop: List<VcsCommitMetadata>): GitCommitEditingOperationResult
  }

  private class GitDropTestOperation : DropTestOperation() {
    override fun execute(context: GitSingleRepoContext, commitsToDrop: List<VcsCommitMetadata>): GitCommitEditingOperationResult {
      return runUnderProgress { GitDropOperation(context.repo).execute(commitsToDrop) }
    }
  }

  private class InMemoryDropTestOperation : DropTestOperation() {
    override fun execute(context: GitSingleRepoContext, commitsToDrop: List<VcsCommitMetadata>): GitCommitEditingOperationResult {
      return runBlocking {
        val testCs = GitDisposable.getInstance(context.project).coroutineScope
        val logData = createLogDataIn(testCs, context.repo, context.logProvider)
        logData.refreshAndWait(context.repo, true)
        InMemoryRebaseOperations.drop(context.repo, commitsToDrop, RebaseEntriesSource.LogData(logData))
      }
    }
  }

  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test drop last commit`(): Unit = with(context) {
    file("a").create().addCommit("Commit a").details()
    file("b").create().addCommit("Commit b").details()
    val commitToDrop = file("c").create().addCommit("Commit c").details()

    refresh()
    updateChangeListManager()

    operation.execute(this, listOf(commitToDrop))

    repo.assertCommitted(1) {
      added("b")
    }
    repo.assertCommitted(2) {
      added("a")
    }
  }

  @Test
  fun `test drop middle commit`(): Unit = with(context) {
    file("a").create().addCommit("Commit a").details()
    val commitToDrop = file("b").create().addCommit("Commit b").details()
    file("c").create().addCommit("Commit c").details()

    refresh()
    updateChangeListManager()

    operation.execute(this, listOf(commitToDrop))

    repo.assertCommitted(1) {
      added("c")
    }
    repo.assertCommitted(2) {
      added("a")
    }
  }

  @Test
  fun `test drop non-linear history`(): Unit = with(context) {
    file("a").create().addCommit("Commit a").details()
    val commitToDropB = file("b").create().addCommit("Commit b").details()
    file("c").create().addCommit("Commit c").details()
    val commitToDropD = file("d").create().addCommit("Commit d").details()
    file("e").create().addCommit("Commit e").details()

    refresh()
    updateChangeListManager()

    operation.execute(this, listOf(commitToDropD, commitToDropB))

    repo.assertCommitted(1) {
      added("e")
    }
    repo.assertCommitted(2) {
      added("c")
    }
    repo.assertCommitted(3) {
      added("a")
    }
  }

  @Test
  fun `test undo dropping of the last commit`(): Unit = with(context) {
    file("a").create().addCommit("Commit a").details()
    file("b").create().addCommit("Commit b").details()
    val commitToDrop = file("c").create().addCommit("Commit c").details()

    refresh()
    updateChangeListManager()

    val operationResult = operation.execute(this, listOf(commitToDrop)) as Complete

    assertThat(runBlocking { operationResult.checkUndoPossibility() }).isInstanceOf(UndoPossibility.Possible::class.java)
    assertThat(operationResult.undo()).isInstanceOf(UndoResult.Success::class.java)

    repo.assertCommitted(1) {
      added("c")
    }
    repo.assertCommitted(2) {
      added("b")
    }
    repo.assertCommitted(3) {
      added("a")
    }
  }
}
