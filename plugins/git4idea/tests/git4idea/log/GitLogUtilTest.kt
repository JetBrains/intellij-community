// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.vcs.Executor.echo
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.util.CollectConsumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.GitCommit
import git4idea.config.GitVersion
import git4idea.history.GitLogUtil
import git4idea.test.GitSingleRepoTest
import git4idea.test.add
import git4idea.test.addCommit
import git4idea.test.last
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

    val actualHashes = ContainerUtil.map<GitCommit, String>(GitLogUtil.collectFullDetails(myProject, repo.root,
                                                                                          "--max-count=$commitCount")
    ) { detail -> detail.id.asString() }

    assertEquals(expected, actualHashes)
  }
}