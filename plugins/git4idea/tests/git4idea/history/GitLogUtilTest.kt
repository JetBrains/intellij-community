// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.GitCommit
import git4idea.config.GitVersion
import git4idea.history.GitCommitRequirements.DiffInMergeCommits
import git4idea.history.GitCommitRequirements.DiffRenameLimit
import git4idea.test.*
import junit.framework.TestCase
import org.junit.Assume.assumeTrue

class GitLogUtilTest : GitSingleRepoTest() {

  @Throws(Exception::class)
  fun testLoadingDetailsWithU0001Character() {
    val details = mutableListOf<VcsFullCommitDetails>()

    val message = "subject containing \u0001 symbol in it\n\ncommit body containing \u0001 symbol in it"
    touch("file.txt", "content")
    repo.addCommit(message)

    GitLogUtil.readFullDetails(myProject, repo.root, CollectConsumer(details))

    val lastCommit = ContainerUtil.getFirstItem(details)
    assertNotNull(lastCommit)
    assertEquals(message, lastCommit!!.fullMessage)
  }

  @Throws(Exception::class)
  fun testLoadingDetailsWithoutChanges() {
    assumeTrue("Not testing: Git doesn't know --allow-empty-message in " + vcs.version,
               vcs.version.isLaterOrEqual(GitVersion(1, 7, 2, 0)))

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
    GitLogUtil.readFullDetails(project, repo.root, Consumer<GitCommit> { actualHashes.add(it.id.asString()) }, "--max-count=$commitCount")
    assertEquals(expected, actualHashes)
  }

  @Throws(Exception::class)
  fun `test readFullDetails without renames`() {
    val details = mutableListOf<VcsFullCommitDetails>()
    touch("fileToRename.txt", "content")
    repo.addCommit("Add fileToRename.txt")
    git("mv fileToRename.txt renamedFile.txt")
    repo.addCommit("Rename fileToRename.txt")

    GitFullDetailsCollector(myProject, repo.root).readFullDetails(CollectConsumer(details),
                                                                  GitCommitRequirements(diffRenameLimit = DiffRenameLimit.NoRenames), false)
    val lastCommit = ContainerUtil.getFirstItem(details)
    assertNotNull(lastCommit)
    assertTrue(lastCommit!!.changes.all { !it.isRenamed })
  }

  @Throws(Exception::class)
  fun `test readFullDetails without merge diff`() {
    `run test for merge diff`(DiffInMergeCommits.NO_DIFF)
  }

  @Throws(Exception::class)
  fun `test readFullDetails with combined merge diff`() {
    `run test for merge diff`(DiffInMergeCommits.COMBINED_DIFF)
  }

  @Throws(Exception::class)
  fun `test readFullDetails with merge diff to parents`() {
    `run test for merge diff`(DiffInMergeCommits.DIFF_TO_PARENTS)
  }

  private fun `run test for merge diff`(diffInMergeCommits: GitCommitRequirements.DiffInMergeCommits) {
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
    TestCase.assertFalse(success)
    repo.add(conflictedFile)
    repo.commit("merge")

    val details = mutableListOf<VcsFullCommitDetails>()
    GitFullDetailsCollector(myProject, repo.root).readFullDetails(CollectConsumer(details),
                                                              GitCommitRequirements(diffInMergeCommits = diffInMergeCommits), false)
    val lastCommit = ContainerUtil.getFirstItem(details)

    assertNotNull(lastCommit)

    when (diffInMergeCommits) {
      DiffInMergeCommits.NO_DIFF -> TestCase.assertTrue(lastCommit.changes.isEmpty())
      DiffInMergeCommits.COMBINED_DIFF -> TestCase.assertEquals(listOf(conflictedFile),
                                                                ChangesUtil.getPaths(lastCommit.changes).map { it.name })
      DiffInMergeCommits.DIFF_TO_PARENTS -> {
        TestCase.assertEquals(listOf(conflictedFile), ChangesUtil.getPaths(lastCommit.changes).map { it.name })
        TestCase.assertEquals(setOf(file1, conflictedFile),
                              ChangesUtil.getPaths(lastCommit.getChanges(0)).mapTo(mutableSetOf()) { it.name })
        TestCase.assertEquals(setOf(file2, conflictedFile),
                              ChangesUtil.getPaths(lastCommit.getChanges(1)).mapTo(mutableSetOf()) { it.name })
      }
    }
  }
}