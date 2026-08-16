// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.changes

import com.intellij.openapi.vcs.changes.Change
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.GitSingleRepoContext
import git4idea.test.assertCommitted
import git4idea.test.assertStagedChanges
import git4idea.test.commit
import git4idea.test.commitDetails
import git4idea.test.file
import git4idea.test.filterChangesByFileName
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitDropSelectedChangesOperationTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test drop multiple new files from middle commit`(): Unit = with(context) {
    file("a").create().addCommit("Add a")

    file("b").create().add()
    file("c").create().add()
    file("d").create().add()
    val targetCommit = commitDetails(commit("Add b, c, d"))

    file("e").create().addCommit("Add e")

    refresh()
    updateChangeListManager()

    val changesToDrop = filterChangesByFileName(targetCommit, listOf("b", "c"))

    executeDropSelectedChangesOperation(changesToDrop, targetCommit) as GitCommitEditingOperationResult.Complete

    file("a").assertExists()
    file("b").assertNotExists()
    file("c").assertNotExists()
    file("d").assertExists()
    file("e").assertExists()
  }

  @Test
  fun `test drop partial changes from modified file`(): Unit = with(context) {
    val newContent = "new content a"
    val oldContent = "old content a"

    file("a").create(oldContent)
    file("a").addCommit("Add a")

    file("b").create().add()
    file("a").write(newContent).add()
    val targetCommit = commitDetails(commit("Add b, modify a"))

    refresh()
    updateChangeListManager()

    val changesToDrop = filterChangesByFileName(targetCommit, listOf("a"))

    executeDropSelectedChangesOperation(changesToDrop, targetCommit) as GitCommitEditingOperationResult.Complete

    assertThat(file("a").read()).describedAs("File 'a' should be restored to original content").isEqualTo(oldContent)
    file("b").assertExists()
  }

  @Test
  fun `test drop file deletion change restores deleted file`(): Unit = with(context) {
    val oldContent = "old content a"
    file("a").create(oldContent).addCommit("Add a")

    file("a").delete().add()
    file("b").create().add()
    val targetCommit = commitDetails(commit("Delete a, add b"))

    refresh()
    updateChangeListManager()

    val changesToDrop = filterChangesByFileName(targetCommit, listOf("a"))

    executeDropSelectedChangesOperation(changesToDrop, targetCommit) as GitCommitEditingOperationResult.Complete

    file("a").assertExists()
    assertThat(file("a").read()).describedAs("File 'a' should have original content").isEqualTo(oldContent)
    file("b").assertExists()
  }

  @Test
  fun `test undo drop selected changes operation`(): Unit = with(context) {
    val oldContent = "old content b"
    file("a").create().addCommit("Add a")

    file("b").create(oldContent).add()
    file("c").create().add()
    val targetCommit = commitDetails(commit("Add b, c"))

    file("d").create().addCommit("Add d")

    refresh()
    updateChangeListManager()

    val changesToDrop = filterChangesByFileName(targetCommit, listOf("b"))

    val result = executeDropSelectedChangesOperation(changesToDrop, targetCommit) as GitCommitEditingOperationResult.Complete

    file("b").assertNotExists()
    file("c").assertExists()

    with(repo) {
      assertCommitted(1) { added("d") }
      assertCommitted(2) { added("c") }
      assertCommitted(3) { added("a") }
    }

    assertThat(runBlocking { result.checkUndoPossibility() })
      .describedAs("Undo should be possible")
      .isInstanceOf(GitCommitEditingOperationResult.Complete.UndoPossibility.Possible::class.java)

    result.undo()

    file("b").assertExists()
    assertThat(file("b").read()).describedAs("File 'b' should have original content").isEqualTo(oldContent)
    file("c").assertExists()
    file("d").assertExists()

    with(repo) {
      assertCommitted(1) { added("d") }
      assertCommitted(2) {
        added("b")
        added("c")
      }
      assertCommitted(3) { added("a") }
    }
  }

  @Test
  fun `test drop changes from initial commit`(): Unit = with(context) {
    file("b").create().add()
    file("c").create().add()
    git("commit --amend --no-edit")

    repo.update()
    val amendedInitialCommit = commitDetails(repo.currentRevision!!)

    val changesToDrop = filterChangesByFileName(amendedInitialCommit, listOf("c"))

    refresh()
    updateChangeListManager()

    executeDropSelectedChangesOperation(changesToDrop, amendedInitialCommit) as GitCommitEditingOperationResult.Complete

    file("b").assertExists()
    file("c").assertNotExists()

    repo.assertCommitted(1) {
      added("b")
      added("initial.txt")
    }
  }

  @Test
  fun `test drop changes preserves existing fixup commits`(): Unit = with(context) {
    file("a").create().addCommit("Add a")

    file("b").create().add()
    file("c").create().add()
    val targetCommit = commitDetails(commit("Add b, c"))

    file("fix").create().add()
    commit("fixup! ${targetCommit.id}")

    file("d").create().addCommit("Add d")

    refresh()
    updateChangeListManager()

    val changesToDrop = filterChangesByFileName(targetCommit, listOf("c"))

    executeDropSelectedChangesOperation(changesToDrop, targetCommit) as GitCommitEditingOperationResult.Complete

    with(repo) {
      assertCommitted(1) { added("d") }
      assertCommitted(2) { added("fix") }
      assertCommitted(3) { added("b") }
      assertCommitted(4) { added("a") }
    }
  }

  @Test
  fun `test drop selected changes doesn't touch local changes`(): Unit = with(context) {
    val oldContent = "old content local"
    file("a").create().addCommit("Add a")

    file("b").create().add()
    file("c").create().add()
    val targetCommit = commitDetails(commit("Add b, c"))

    file("local-staged").create().add()
    file("local").create(oldContent)

    val changesToDrop = filterChangesByFileName(targetCommit, listOf("b"))

    refresh()
    updateChangeListManager()

    executeDropSelectedChangesOperation(changesToDrop, targetCommit) as GitCommitEditingOperationResult.Complete

    file("local").assertExists()
    assertThat(file("local").read()).describedAs("Local unstaged file should preserve content").isEqualTo(oldContent)

    with(repo) {
      assertStagedChanges { added("local-staged") }
      assertCommitted(1) { added("c") }
      assertCommitted(2) { added("a") }
    }
  }

  @Test
  fun `test drop selected changes fails due to the rebase fail and restores saved local changes`(): Unit = with(context) {
    file("a").create().addCommit("Add a")

    file("b").create().add()
    file("c").create().add()
    val targetCommit = commitDetails(commit("Add b, c"))

    file("d").create().addCommit("Add d")

    val localChange = "local change"
    file("c").append(localChange)

    refresh()
    updateChangeListManager()

    git.setShouldRebaseFail { true }

    val changesToDrop = filterChangesByFileName(targetCommit, listOf("b"))

    val result = executeDropSelectedChangesOperation(changesToDrop, targetCommit)

    assertThat(result)
      .describedAs("Operation should fail due to rebase fail")
      .isInstanceOf(GitCommitEditingOperationResult.Incomplete::class.java)

    with(repo) {
      assertCommitted(1) { deleted("b") } // fixup commit
      assertCommitted(2) {
        added("d")
      }
    }

    file("c").assertExists()
    assertThat(file("c").read()).describedAs("Local changes should be preserved").isEqualTo(localChange)
  }

  @Test
  fun `test drop all changes from middle commit fails in rebase`(): Unit = with(context) {
    file("a").create().addCommit("Add a")

    file("b").create().add()
    file("c").create().add()
    val targetCommit = commitDetails(commit("Add b, c"))

    file("d").create().addCommit("Add d")

    refresh()
    updateChangeListManager()

    val allChangesToDrop = targetCommit.changes.toList()

    val result = executeDropSelectedChangesOperation(allChangesToDrop, targetCommit)

    assertThat(result)
      .describedAs("Operation should fail when trying to create empty commit")
      .isInstanceOf(GitCommitEditingOperationResult.Incomplete::class.java)

    assertThat(vcsNotifier.notifications)
      .anyMatch { it.title == GitBundle.message("rebase.notification.failed.rebase.title") }
  }

  @Test
  fun `test drop all changes from the last commit fails`(): Unit = with(context) {
    file("a").create().addCommit("Add a")

    file("b").create().add()
    file("c").create().add()
    val targetCommit = commitDetails(commit("Add b, c"))

    refresh()
    updateChangeListManager()

    val allChangesToDrop = targetCommit.changes.toList()

    val result = executeDropSelectedChangesOperation(allChangesToDrop, targetCommit)

    assertThat(result)
      .describedAs("Operation should fail when trying to create empty commit")
      .isInstanceOf(GitCommitEditingOperationResult.Incomplete::class.java)

    assertThat(vcsNotifier.notifications)
      .anyMatch { it.title == GitBundle.message("rebase.log.changes.drop.failed.title") }
  }

  private fun GitSingleRepoContext.executeDropSelectedChangesOperation(
    changes: List<Change>,
    targetCommit: VcsCommitMetadata,
  ): GitCommitEditingOperationResult =
    runBlocking {
      GitDropSelectedChangesOperation(repo, targetCommit, changes).execute()
    }
}
