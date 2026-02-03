// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory.rebase.log.changes

import git4idea.i18n.GitBundle
import git4idea.inMemory.rebase.log.GitInMemoryOperationTest
import git4idea.rebase.log.GitCommitEditingOperationResult
import git4idea.test.assertCommitted
import git4idea.test.assertLastMessage
import git4idea.test.commit
import git4idea.test.filterChangesByFileName

internal class GitExtractSelectedChangesOperationTest : GitInMemoryOperationTest() {
  fun `test extract single file from middle commit`() {
    file("a").create().addCommit("Add a")

    file("b").create().add()
    file("c").create().add()
    val targetCommit = commitDetails(commit("Add b, c"))

    file("d").create().addCommit("Add d")

    refresh()
    updateChangeListManager()

    val changesToExtract = filterChangesByFileName(targetCommit, listOf("b"))
    val newMessage = "Extract b"

    GitExtractSelectedChangesOperation(objectRepo, targetCommit, newMessage, changesToExtract).run()
      as GitCommitEditingOperationResult.Complete

    file("b").assertExists()
    file("c").assertExists()
    file("d").assertExists()

    with(repo) {
      assertCommitted(1) { added("d") }
      assertCommitted(2) { added("b") }
      assertCommitted(3) { added("c") }
      assertCommitted(4) { added("a") }
    }
  }

  fun `test extract nested directory structure`() {
    file("a").create().addCommit("Add a")

    file("src/main/App.java").create().add()
    file("src/test/AppTest.java").create().add()
    file("README.md").create().add()
    val targetCommit = commitDetails(commit("Add project structure"))

    file("b").create().addCommit("Add b")

    refresh()
    updateChangeListManager()

    val changesToExtract = filterChangesByFileName(targetCommit, listOf("App.java", "AppTest.java"))
    val newMessage = "Extract src directory"

    GitExtractSelectedChangesOperation(objectRepo, targetCommit, newMessage, changesToExtract).run()
      as GitCommitEditingOperationResult.Complete

    file("src/main/App.java").assertExists()
    file("src/test/AppTest.java").assertExists()
    file("README.md").assertExists()

    with(repo) {
      assertCommitted(1) { added("b") }
      assertCommitted(2) {
        added("src/main/App.java")
        added("src/test/AppTest.java")
      }
      assertCommitted(3) { added("README.md") }
      assertCommitted(4) { added("a") }
    }
  }

  fun `test extract from initial commit`() {
    file("b").create().add()
    file("c").create().add()
    git("commit --amend --no-edit")

    repo.update()
    val amendedInitialCommit = commitDetails(repo.currentRevision!!)

    refresh()
    updateChangeListManager()

    val changesToExtract = filterChangesByFileName(amendedInitialCommit, listOf("b"))
    val newMessage = "Extract b from initial"

    GitExtractSelectedChangesOperation(objectRepo, amendedInitialCommit, newMessage, changesToExtract).run()
      as GitCommitEditingOperationResult.Complete

    assertLastMessage(newMessage)

    with(repo) {
      assertCommitted(1) { added("b") }
      assertCommitted(2) {
        added("c")
        added("initial.txt")
      }
    }
  }

  fun `test extract removal of a file`() {
    file("a").create("content a").addCommit("Add a")
    file("b").create("content b").addCommit("Add b")

    file("b").delete().add()
    file("c").create("content c").add()
    val targetCommit = commitDetails(commit("Remove b, add c"))

    file("d").create("content d").addCommit("Add d")

    refresh()
    updateChangeListManager()

    val changesToExtract = filterChangesByFileName(targetCommit, listOf("b"))
    val newMessage = "Extract removal of b"

    GitExtractSelectedChangesOperation(objectRepo, targetCommit, newMessage, changesToExtract).run()
      as GitCommitEditingOperationResult.Complete

    file("b").assertNotExists()
    file("c").assertExists()
    file("d").assertExists()

    with(repo) {
      assertCommitted(1) { added("d") }
      assertCommitted(2) { deleted("b") }
      assertCommitted(3) { added("c") }
      assertCommitted(4) { added("b") }
      assertCommitted(5) { added("a") }
    }
  }

  fun `test extract from commit where file becomes directory`() {
    file("component").create("component content").addCommit("Create component")

    file("component").delete().add()
    file("component/A.java").create("content A").add()
    file("component/B.java").create("content B").add()
    file("README.md").create("content README").add()

    val commit = commitDetails(commit("Create component dir"))

    refresh()
    updateChangeListManager()

    val fileRemoval = filterChangesByFileName(commit, listOf("component"))
    GitExtractSelectedChangesOperation(objectRepo, commit, "Extract component file removal", fileRemoval).run()
      as GitCommitEditingOperationResult.Incomplete

    assertErrorNotification(GitBundle.message("in.memory.rebase.log.changes.extract.failed.title"), GitBundle.message("in.memory.split.tree.mixed.error"))

    val filesCreation = filterChangesByFileName(commit, listOf("A.java", "B.java"))
    val result = GitExtractSelectedChangesOperation(objectRepo, commit, "Extract component files creation", filesCreation).run()
      as GitCommitEditingOperationResult.Complete

    with(repo) {
      assertCommitted(1) {
        added("component/A.java")
        added("component/B.java")
      }
      assertCommitted(2) {
        deleted("component")
        added("README.md")
      }
    }

    result.undo()

    GitExtractSelectedChangesOperation(objectRepo, commit, "Extract file removal and component files creation", filesCreation + fileRemoval).run()
      as GitCommitEditingOperationResult.Complete

    with(repo) {
      assertCommitted(1) {
        added("component/A.java")
        added("component/B.java")
        deleted("component")
      }
      assertCommitted(2) {
        added("README.md")
      }
    }
  }

  fun `test extract from commit where directory becomes file`() {
    file("component/A.java").create("content A").add()
    file("component/B.java").create("content B").add()
    commit("Create component dir")

    file("component").delete().add()
    file("component").create("component content").add()
    file("README.md").create("content README").add()

    val commit = commitDetails(commit("Remove component dir, create component file"))

    refresh()
    updateChangeListManager()

    val fileCreation = filterChangesByFileName(commit, listOf("component"))
    val result = GitExtractSelectedChangesOperation(objectRepo, commit, "Extract component file creation", fileCreation).run()
      as GitCommitEditingOperationResult.Complete

    with(repo) {
      assertCommitted(1) {
        added("component")
      }
    }

    result.undo()

    val directoryFileRemoval = filterChangesByFileName(commit, listOf("A.java"))
    GitExtractSelectedChangesOperation(objectRepo, commit, "Extract component directory file removal", directoryFileRemoval).run()
      as GitCommitEditingOperationResult.Incomplete

    assertErrorNotification(GitBundle.message("in.memory.rebase.log.changes.extract.failed.title"), GitBundle.message("in.memory.split.tree.mixed.error"))

    val directoryRemovalAndFileCreation = filterChangesByFileName(commit, listOf("A.java", "B.java", "component"))

    GitExtractSelectedChangesOperation(objectRepo, commit, "Extract directory removal and file creation", directoryRemovalAndFileCreation).run()
      as GitCommitEditingOperationResult.Complete

    with(repo) {
      assertCommitted(1) {
        added("component")
        deleted("component/A.java")
        deleted("component/B.java")
      }
      assertCommitted(2) {
        added("README.md")
      }
      assertCommitted(3) {
        added("component/A.java")
        added("component/B.java")
      }
    }
  }
}