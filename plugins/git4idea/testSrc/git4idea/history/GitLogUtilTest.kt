// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.util.CollectConsumer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.config.GitVersion
import git4idea.history.GitCommitRequirements.DiffInMergeCommits
import git4idea.history.GitCommitRequirements.DiffRenames
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.add
import git4idea.test.addCommit
import git4idea.test.checkout
import git4idea.test.checkoutNew
import git4idea.test.commit
import git4idea.test.git
import git4idea.test.last
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

@TestApplication
class GitLogUtilTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Throws(Exception::class)
  @Test
  fun testLoadingDetailsWithU0001Character(): Unit = with(context) {
    val details = mutableListOf<VcsFullCommitDetails>()

    val message = "subject containing \u0001 symbol in it\n\ncommit body containing \u0001 symbol in it"
    touch("file.txt", "content")
    repo.addCommit(message)

    GitLogUtil.readFullDetails(project, repo.root, CollectConsumer(details))

    val lastCommit = details.firstOrNull()
    assertThat(lastCommit).isNotNull()
    assertThat(lastCommit!!.fullMessage).isEqualTo(message)
  }

  @Throws(Exception::class)
  @Test
  fun testLoadingDetailsWithoutChanges(): Unit = with(context) {
    assumeTrue(vcs.version.isLaterOrEqual(GitVersion(1, 7, 2, 0)),
               "Not testing: Git doesn't know --allow-empty-message in " + vcs.version)

    val expected: MutableList<String> = mutableListOf()

    val messageFile = "message.txt"
    touch(messageFile, "")

    val commitCount = 20
    for (i in 0 until commitCount) {
      echo("file.txt", "content number $i")
      repo.add()
      git("commit --allow-empty-message -F $messageFile")
      expected.add(this.last())
    }
    expected.reverse()

    val actualHashes = mutableListOf<String>()
    GitLogUtil.readFullDetails(project, repo.root, { actualHashes.add(it.id.asString()) }, "--max-count=$commitCount")
    assertThat(actualHashes).isEqualTo(expected)
  }

  @Throws(Exception::class)
  @Test
  fun `test readFullDetails without renames`(): Unit = with(context) {
    val details = mutableListOf<VcsFullCommitDetails>()
    touch("fileToRename.txt", "content")
    repo.addCommit("Add fileToRename.txt")
    git("mv fileToRename.txt renamedFile.txt")
    repo.addCommit("Rename fileToRename.txt")

    GitFullDetailsCollector(project, repo.root).readFullDetails(CollectConsumer(details),
                                                                GitCommitRequirements(diffRenames = DiffRenames.NoRenames), false)
    val lastCommit = details.firstOrNull()
    assertThat(lastCommit).isNotNull()
    assertThat(lastCommit!!.changes.all { !it.isRenamed }).isTrue()
  }

  @Throws(Exception::class)
  @Test
  fun `test readFullDetails without merge diff`() {
    `run test for merge diff`(DiffInMergeCommits.NO_DIFF)
  }

  @Throws(Exception::class)
  @Test
  fun `test readFullDetails with combined merge diff`() {
    `run test for merge diff`(DiffInMergeCommits.COMBINED_DIFF)
  }

  @Throws(Exception::class)
  @Test
  fun `test readFullDetails with merge diff to parents`() {
    `run test for merge diff`(DiffInMergeCommits.DIFF_TO_PARENTS)
  }

  private fun `run test for merge diff`(diffInMergeCommits: DiffInMergeCommits): Unit = with(context) {
    val file1 = "fileToMerge1.txt"
    val file2 = "fileToMerge2.txt"
    val conflictedFile = "fileToMergeWithConflict.txt"

    touch(conflictedFile, "content")
    repo.addCommit("Add $conflictedFile")
    repo.checkoutNew("testBranch")
    touch(file1, "content")
    overwrite(conflictedFile, "content\nbranch1")
    repo.addCommit("Add $file1 and change $conflictedFile")

    repo.checkout("master")
    touch(file2, "content")
    overwrite(conflictedFile, "branch2\ncontent")
    repo.addCommit("Add $file2 and change $conflictedFile")

    val success = git.merge(repo, "testBranch", mutableListOf("--no-ff")).success()
    assertThat(success).isFalse()
    repo.add(conflictedFile)
    repo.commit("merge")

    val details = mutableListOf<VcsFullCommitDetails>()
    GitFullDetailsCollector(project, repo.root).readFullDetails(CollectConsumer(details),
                                                                GitCommitRequirements(diffInMergeCommits = diffInMergeCommits), false)
    val lastCommit = details.firstOrNull()

    assertThat(lastCommit).isNotNull()

    when (diffInMergeCommits) {
      DiffInMergeCommits.NO_DIFF -> assertThat(lastCommit!!.changes.isEmpty()).isTrue()
      DiffInMergeCommits.COMBINED_DIFF -> assertThat(ChangesUtil.getPaths(lastCommit!!.changes).map { it.name }).isEqualTo(listOf(
        conflictedFile))
      DiffInMergeCommits.DIFF_TO_PARENTS -> {
        assertThat(ChangesUtil.getPaths(lastCommit!!.changes).map { it.name }).isEqualTo(listOf(conflictedFile))
        assertThat(ChangesUtil.getPaths(lastCommit.getChanges(0)).mapTo(mutableSetOf()) { it.name }).isEqualTo(setOf(file1, conflictedFile))
        assertThat(ChangesUtil.getPaths(lastCommit.getChanges(1)).mapTo(mutableSetOf()) { it.name }).isEqualTo(setOf(file2, conflictedFile))
      }
      else -> {}
    }
  }
}