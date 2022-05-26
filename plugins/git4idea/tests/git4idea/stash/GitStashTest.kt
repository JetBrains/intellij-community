// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash

import com.intellij.openapi.vcs.Executor.echo
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.history.GitLogUtil
import git4idea.test.*
import git4idea.ui.StashInfo
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

    assertStashInfoIsCorrect(0, msg2, branch, stash2)
    assertStashInfoIsCorrect(1, msg1, "master", stash1)
  }

  private fun assertStashInfoIsCorrect(expectedNumber: Int,
                                       expectedMessage: String,
                                       expectedBranch: String,
                                       actualStash: StashInfo) {
    assertEquals("stash@{$expectedNumber}", actualStash.stash)
    assertStashMessageEquals(expectedMessage, actualStash)
    assertEquals("WIP on $expectedBranch", actualStash.branch)
    assertEquals(stashAuthorTime(expectedNumber), actualStash.authorTime)
  }

  private fun assertStashMessageEquals(expectedMessage: String, stash: StashInfo) {
    assertTrue("Expected '<HASH> $expectedMessage', got '${stash.message}'",
               stashMessagePattern(expectedMessage).matcher(stash.message).matches())
  }

  private fun stashMessagePattern(commitMessage: String) = Pattern.compile("${VcsLogUtil.HASH_REGEX.pattern()} ${commitMessage}")

  private fun stash() = git(project, "stash")

  private fun stashAuthorTime(stashNumber: Int): Long {
    val noWalkParameter = GitLogUtil.getNoWalkParameter(project)
    val timeString = git(project, "log --pretty=format:%at $noWalkParameter stash@{$stashNumber}")
    return GitLogUtil.parseTime(timeString)
  }
}