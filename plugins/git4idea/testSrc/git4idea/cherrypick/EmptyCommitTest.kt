// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick

import git4idea.GitUtil
import com.intellij.testFramework.junit5.TestApplication
import git4idea.i18n.GitBundle.message
import git4idea.test.GitSingleRepoContext
import git4idea.test.addCommit
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class EmptyCommitTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  private val fallbackMessage = message("cherry.pick.empty.cherry.pick.commit")

  @Test
  fun `test create empty commit succeeds when no staged changes`(): Unit = with(context) {
    file("file.txt").create("initial content").add()
    addCommit("Initial commit")

    // Verify no staged changes
    assertThat(hasLocalChanges(staged = true)).isFalse()

    // Create empty commit
    val result = createEmptyCommit()

    assertThat(result.success()).isTrue()
    assertCommitMessage(fallbackMessage)
  }

  @Test
  fun `test create empty commit fails when staged changes exist`(): Unit = with(context) {
    val file = file("file.txt").create("initial content").add()
    addCommit("Initial commit")

    // Stage some changes
    file.append("new content")
    git("add file.txt")

    // Verify staged changes exist
    assertThat(hasLocalChanges(staged = true)).isTrue()

    // Attempt to create empty commit
    val result = createEmptyCommit()

    assertThat(result.success()).isFalse()
    assertThat(result.errorOutputAsJoinedString).contains("staged changes exist")
  }

  @Test
  fun `test create empty commit uses MERGE_MSG file when present`(): Unit = with(context) {
    file("file.txt").create("initial content").add()
    addCommit("Initial commit")

    // Create MERGE_MSG file
    val mergeMsg = "Cherry-picked commit message\n\n(cherry picked from commit abc123)"
    repo.repositoryFiles.mergeMessageFile.writeText(mergeMsg)

    val result = createEmptyCommit()

    assertThat(result.success()).isTrue()
    assertCommitMessage(mergeMsg.trim())
  }

  @Test
  fun `test create empty commit uses fallback message when MERGE_MSG missing`(): Unit = with(context) {
    file("file.txt").create("initial content").add()
    addCommit("Initial commit")

    // Ensure MERGE_MSG doesn't exist
    val mergeMsgFile = repo.repositoryFiles.mergeMessageFile
    if (mergeMsgFile.exists()) {
      mergeMsgFile.delete()
    }

    val result = createEmptyCommit()

    assertThat(result.success()).isTrue()
    assertCommitMessage(fallbackMessage)
  }

  @Test
  fun `test hasLocalChanges ignores unstaged changes`(): Unit = with(context) {
    val file = file("file.txt").create("initial content").add()
    addCommit("Initial commit")

    file.append("new content")
    // Don't stage the changes

    assertThat(hasLocalChanges(staged = true)).isFalse()
    assertThat(hasLocalChanges(staged = false)).isTrue()
  }

  private fun GitSingleRepoContext.assertCommitMessage(expectedMessage: String) {
    val lastCommitMessage = git("log -1 --pretty=%B").trim()
    assertThat(lastCommitMessage).isEqualTo(expectedMessage)
  }

  private fun GitSingleRepoContext.hasLocalChanges(staged: Boolean): Boolean {
    return try {
      GitUtil.hasLocalChanges(staged, project, repo.root)
    }
    catch (_: Exception) {
      false
    }
  }

  private fun GitSingleRepoContext.createEmptyCommit() = EmptyCherryPickResolutionStrategy.CREATE_EMPTY.apply(repo)

}