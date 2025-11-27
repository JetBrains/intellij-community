// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick

import git4idea.GitUtil
import git4idea.i18n.GitBundle.message
import git4idea.repo.GitRepository
import git4idea.test.GitSingleRepoTest
import git4idea.test.addCommit
import org.junit.Test

class EmptyCommitTest : GitSingleRepoTest() {

  private val fallbackMessage = message("cherry.pick.empty.cherry.pick.commit")
  fun `test create empty commit succeeds when no staged changes`() {
    val file = file("file.txt").create("initial content").add()
    addCommit("Initial commit")

    // Verify no staged changes
    assertFalse("Should have no staged changes", repo.hasLocalChanges(staged = true))

    // Create empty commit
    val result = repo.createEmptyCommit()

    assertTrue("Empty commit should succeed", result.success())
    assertCommitMessage(fallbackMessage)
  }

  fun `test create empty commit fails when staged changes exist`() {
    val file = file("file.txt").create("initial content").add()
    addCommit("Initial commit")

    // Stage some changes
    file.append("new content")
    git("add file.txt")

    // Verify staged changes exist
    assertTrue("Should have staged changes", repo.hasLocalChanges(staged = true))

    // Attempt to create empty commit
    val result = repo.createEmptyCommit()

    assertFalse("Empty commit should fail when staged changes exist", result.success())
    assertErrorContains(result, "staged changes exist")
  }

  fun `test create empty commit uses MERGE_MSG file when present`() {
    val file = file("file.txt").create("initial content").add()
    addCommit("Initial commit")

    // Create MERGE_MSG file
    val mergeMsg = "Cherry-picked commit message\n\n(cherry picked from commit abc123)"
    repo.repositoryFiles.mergeMessageFile.writeText(mergeMsg)

    val result = repo.createEmptyCommit()

    assertTrue("Empty commit should succeed", result.success())
    assertCommitMessage(mergeMsg.trim())
  }

  fun `test create empty commit uses fallback message when MERGE_MSG missing`() {
    val file = file("file.txt").create("initial content").add()
    addCommit("Initial commit")

    // Ensure MERGE_MSG doesn't exist
    val mergeMsgFile = repo.repositoryFiles.mergeMessageFile
    if (mergeMsgFile.exists()) {
      mergeMsgFile.delete()
    }

    val result = repo.createEmptyCommit()

    assertTrue("Empty commit should succeed", result.success())
    assertCommitMessage(fallbackMessage)
  }

  fun `test hasLocalChanges ignores unstaged changes`() {
    val file = file("file.txt").create("initial content").add()
    addCommit("Initial commit")

    file.append("new content")
    // Don't stage the changes

    assertFalse("Should not detect unstaged changes when checking staged",
                repo.hasLocalChanges(staged = true))
    assertTrue("Should detect changes in working tree",
               repo.hasLocalChanges(staged = false))
  }

  private fun assertCommitMessage(expectedMessage: String) {
    val lastCommitMessage = git("log -1 --pretty=%B").trim()
    assertEquals("Commit message should match", expectedMessage, lastCommitMessage)
  }

  private fun assertErrorContains(result: git4idea.commands.GitCommandResult, substring: String) {
    val error = result.errorOutputAsJoinedString
    assertTrue("Error should contain '$substring', but was: $error",
               error.contains(substring))
  }

  private fun GitRepository.hasLocalChanges(staged: Boolean): Boolean {
    return try {
      GitUtil.hasLocalChanges(staged, project, root)
    } catch (e: Exception) {
      false
    }
  }

  private fun GitRepository.createEmptyCommit() = EmptyCherryPickResolutionStrategy.CREATE_EMPTY.apply(this)

}