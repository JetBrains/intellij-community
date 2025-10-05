// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log.changes

import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.GitSingleRepoTest
import git4idea.test.assertCommitted
import git4idea.test.assertStagedChanges
import git4idea.test.commit
import git4idea.test.filterChangesByFileName

import kotlinx.coroutines.runBlocking

class GitDropSelectedChangesOperationTest : GitSingleRepoTest() {
  fun `test drop multiple new files from middle commit`() {
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

  fun `test drop partial changes from modified file`() {
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

    val currentContent = file("a").read()
    assertEquals("File 'a' should be restored to original content", oldContent, currentContent)
    file("b").assertExists()
  }

  fun `test drop file deletion change restores deleted file`() {
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
    assertEquals("File 'a' should have original content", oldContent, file("a").read())
    file("b").assertExists()
  }

  fun `test undo drop selected changes operation`() {
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

    val undoPossibility = result.checkUndoPossibility()
    assertTrue("Undo should be possible", undoPossibility is GitCommitEditingOperationResult.Complete.UndoPossibility.Possible)

    result.undo()

    file("b").assertExists()
    assertEquals("File 'b' should have original content", oldContent, file("b").read())
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

  fun `test drop changes from initial commit`() {
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

  fun `test drop changes preserves existing fixup commits`() {
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

  fun `test drop selected changes doesn't touch local changes`() {
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
    assertEquals("Local unstaged file should preserve content", oldContent, file("local").read())

    with(repo) {
      assertStagedChanges { added("local-staged") }
      assertCommitted(1) { added("c") }
      assertCommitted(2) { added("a") }
    }
  }

  fun `test drop selected changes fails due to the rebase fail and notifies about saved changes`() {
    file("a").create().addCommit("Add a")

    file("b").create().add()
    file("c").create().add()
    val targetCommit = commitDetails(commit("Add b, c"))

    file("d").create().addCommit("Add d")

    file("c").append("local change")

    refresh()
    updateChangeListManager()

    git.setShouldRebaseFail { true }

    val changesToDrop = filterChangesByFileName(targetCommit, listOf("c"))

    val result = executeDropSelectedChangesOperation(changesToDrop, targetCommit)

    assertTrue("Operation should fail due to rebase fail", result is GitCommitEditingOperationResult.Incomplete)

    file("c").assertNotExists()

    val gitSettings = GitVcsSettings.getInstance(project)
    val savePolicy = gitSettings.saveChangesPolicy
    assertWarningNotification(
      GitBundle.message("restore.notification.failed.title"),
      savePolicy.selectBundleMessage(
        GitBundle.message("restore.notification.failed.stash.message", GitBundle.message("rebase.log.changes.action.operation.drop.name")),
        GitBundle.message("restore.notification.failed.shelf.message", GitBundle.message("rebase.log.changes.action.operation.drop.name"))
      )
    )
  }

  fun `test drop all changes from middle commit fails in rebase`() {
    file("a").create().addCommit("Add a")

    file("b").create().add()
    file("c").create().add()
    val targetCommit = commitDetails(commit("Add b, c"))

    file("d").create().addCommit("Add d")

    refresh()
    updateChangeListManager()

    val allChangesToDrop = targetCommit.changes.toList()

    val result = executeDropSelectedChangesOperation(allChangesToDrop, targetCommit)

    assertTrue("Operation should fail when trying to create empty commit", result is GitCommitEditingOperationResult.Incomplete)

    assertNotNull(vcsNotifier.notifications.find { it.title == GitBundle.message("rebase.notification.failed.rebase.title") })
  }

  fun `test drop all changes from the last commit fails`() {
    file("a").create().addCommit("Add a")

    file("b").create().add()
    file("c").create().add()
    val targetCommit = commitDetails(commit("Add b, c"))

    refresh()
    updateChangeListManager()

    val allChangesToDrop = targetCommit.changes.toList()

    val result = executeDropSelectedChangesOperation(allChangesToDrop, targetCommit)

    assertTrue("Operation should fail when trying to create empty commit", result is GitCommitEditingOperationResult.Incomplete)

    assertNotNull(vcsNotifier.notifications.find { it.title == GitBundle.message("rebase.log.changes.drop.failed.title") })
  }

  private fun executeDropSelectedChangesOperation(changes: List<Change>, targetCommit: VcsCommitMetadata): GitCommitEditingOperationResult =
    runBlocking {
      GitDropSelectedChangesOperation(repo, targetCommit, changes).execute()
    }
}