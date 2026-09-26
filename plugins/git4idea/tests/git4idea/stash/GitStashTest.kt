// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash

import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitUtil
import git4idea.history.GitLogUtil
import git4idea.test.GitSingleRepoContext
import git4idea.test.add
import git4idea.test.branch
import git4idea.test.checkout
import git4idea.test.commit
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.ui.StashInfo
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

@TestApplication
class GitStashTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `test list stashes`(): Unit = with(context) {

    val msg1 = "message 1"
    touch("a.txt")
    add()
    commit(msg1)

    echo("a.txt", RandomStringUtils.secure().nextAlphanumeric(200))
    stash()

    val branch = "test"
    repo.branch(branch)
    checkout(branch)

    val msg2 = "message 2"
    touch("b.txt")
    add()
    commit(msg2)

    echo("b.txt", RandomStringUtils.secure().nextAlphanumeric(200))
    stash()

    val stack = loadStashStack(project, projectRoot)

    assertThat(stack).hasSize(2)

    val stash2 = stack.first()
    val stash1 = stack.last()

    assertStashInfoIsCorrect(0, msg2, branch, stash2)
    assertStashInfoIsCorrect(1, msg1, "master", stash1)
  }

  private fun GitSingleRepoContext.assertStashInfoIsCorrect(expectedNumber: Int,
                                                            expectedMessage: String,
                                                            expectedBranch: String,
                                                            actualStash: StashInfo) {
    assertThat(actualStash.stash).isEqualTo("stash@{$expectedNumber}")
    assertStashMessageEquals(expectedMessage, actualStash)
    assertThat(actualStash.branch).isEqualTo("WIP on $expectedBranch")
    assertThat(actualStash.authorTime).isEqualTo(stashAuthorTime(expectedNumber))
  }

  private fun assertStashMessageEquals(expectedMessage: String, stash: StashInfo) {
    assertThat(stashMessagePattern(expectedMessage).matcher(stash.message).matches())
      .describedAs("Expected '<HASH> $expectedMessage', got '${stash.message}'")
      .isTrue()
  }

  private fun stashMessagePattern(commitMessage: String) = Pattern.compile("${GitUtil.HASH_REGEX.pattern()} ${commitMessage}")

  private fun GitSingleRepoContext.stash() = git(project, "stash")

  private fun GitSingleRepoContext.stashAuthorTime(stashNumber: Int): Long {
    val noWalkParameter = GitLogUtil.getNoWalkParameter(project)
    val timeString = git(project, "log --pretty=format:%at $noWalkParameter stash@{$stashNumber}")
    return GitLogUtil.parseTime(timeString)
  }
}