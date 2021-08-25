// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash

import com.intellij.openapi.vcs.Executor.echo
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.test.*
import git4idea.test.git
import git4idea.ui.StashInfo
import junit.framework.TestCase
import org.apache.commons.lang.RandomStringUtils
import java.util.regex.Pattern

class GitStashTest : GitSingleRepoTest() {
  fun `test list stashes`() {

    val msg1 = "message 1"
    touch("a.txt")
    add()
    commit(msg1)

    echo("a.txt", RandomStringUtils.randomAlphanumeric(200))
    stash()

    val branch = "test"
    branch(branch)
    checkout(branch)

    val msg2 = "message 2"
    touch("b.txt")
    add()
    commit(msg2)

    echo("b.txt", RandomStringUtils.randomAlphanumeric(200))
    stash()

    val stack = loadStashStack(project, projectRoot)

    assertEquals(2, stack.size)

    val stash2 = stack.first()
    val stash1 = stack.last()


    assertEquals("stash@{0}", stash2.stash)
    assertStashMessageEquals(msg2, stash2)
    TestCase.assertEquals("WIP on $branch", stash2.branch)
    assertEquals("stash@{1}", stash1.stash)
    assertStashMessageEquals(msg1, stash1)
    TestCase.assertEquals("WIP on master", stash1.branch)
  }

  private fun assertStashMessageEquals(expectedMessage: String, stash: StashInfo) {
    assertTrue("Expected '<HASH> $expectedMessage', got '${stash.message}'",
               stashMessagePattern(expectedMessage).matcher(stash.message).matches())
  }

  private fun stashMessagePattern(commitMessage: String) = Pattern.compile("${VcsLogUtil.HASH_REGEX.pattern()} ${commitMessage}")

  private fun stash() = git(project, "stash")
}