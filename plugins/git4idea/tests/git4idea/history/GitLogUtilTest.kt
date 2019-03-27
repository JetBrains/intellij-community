// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.vcs.Executor.echo
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.GitCommit
import git4idea.config.GitVersion
import git4idea.test.*
import junit.framework.TestCase
import org.junit.Assume.assumeTrue

class GitLogUtilTest : GitSingleRepoTest() {

  @Throws(Exception::class)
  fun testLoadingDetailsWithU0001Character() {
    val details = ContainerUtil.newArrayList<VcsFullCommitDetails>()

    val message = "subject containing \u0001 symbol in it\n\ncommit body containing \u0001 symbol in it"
    touch("file.txt", "content")
    repo.addCommit(message)

    GitLogUtil.readFullDetails(myProject, repo.root, CollectConsumer(details), true, true, true)

    val lastCommit = ContainerUtil.getFirstItem(details)
    assertNotNull(lastCommit)
    assertEquals(message, lastCommit!!.fullMessage)
  }

  @Throws(Exception::class)
  fun testLoadingDetailsWithoutChanges() {
    assumeTrue("Not testing: Git doesn't know --allow-empty-message in " + vcs.version,
               vcs.version.isLaterOrEqual(GitVersion(1, 7, 2, 0)))

    val expected: MutableList<String> = ContainerUtil.newArrayList()

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
    GitLogUtil.readFullDetails(project, repo.root, Consumer<GitCommit> { actualHashes.add(it.id.asString()) },
                               true, true, false, "--max-count=$commitCount")
    assertEquals(expected, actualHashes)
  }

  @Throws(Exception::class)
  fun `test readFullDetails without renames`() {
    val details = ContainerUtil.newArrayList<VcsFullCommitDetails>()
    touch("fileToRename.txt", "content")
    repo.addCommit("Add fileToRename.txt")
    git("mv fileToRename.txt renamedFile.txt")
    repo.addCommit("Rename fileToRename.txt")

    GitLogUtil.readFullDetails(myProject, repo.root, CollectConsumer(details), true, true, true, false, true)
    val lastCommit = ContainerUtil.getFirstItem(details)
    assertNotNull(lastCommit)
    assertTrue(lastCommit!!.changes.all { !it.isRenamed })
  }

  @Throws(Exception::class)
  fun `test readFullDetails without fullMergeDiff`() {
    `run test for merge diff`(false)
  }

  @Throws(Exception::class)
  fun `test readFullDetails with fullMergeDiff`() {
    `run test for merge diff`(true)
  }

  private fun `run test for merge diff`(withMergeDiff: Boolean) {
    repo.checkoutNew("testBranch")
    touch("fileToMerge1.txt", "content")
    repo.addCommit("Add fileToMerge1.txt")
    repo.checkout("master")
    touch("fileToMerge2.txt", "content")
    repo.addCommit("Add fileToMerge2.txt")
    val success = git.merge(repo, "testBranch", mutableListOf("--no-ff")).success()
    if (!success) {
      fail("Could not do a merge")
    }

    val details = ContainerUtil.newArrayList<VcsFullCommitDetails>()
    GitLogUtil.readFullDetails(myProject, repo.root, CollectConsumer(details), true, true, true, true, withMergeDiff)
    val lastCommit = ContainerUtil.getFirstItem(details)

    assertNotNull(lastCommit)
    TestCase.assertEquals(withMergeDiff, !lastCommit!!.getChanges(0).isEmpty())
  }
}