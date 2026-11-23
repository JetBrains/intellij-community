// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.impl.HashImpl
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
import git4idea.ui.StashInfo.Companion.subject
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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

  @ParameterizedTest
  @ValueSource(strings = ["20240403402", "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef", "deadbeef custom message"])
  fun `test custom stash subject`(message: String): Unit = with(context) {
    touch("a.txt")
    add()
    commit("initial")

    echo("a.txt", "changed")
    stash(message)

    val stash = loadStashStack(project, projectRoot).single()
    assertThat(stash.branch).isEqualTo("On master")
    assertThat(stash.subject).isEqualTo(message)
  }

  @ParameterizedTest
  @ValueSource(ints = [8, 64])
  fun `test custom stash subject keeps matching base hash`(hashLength: Int): Unit = with(context) {
    touch("a.txt")
    add()
    commit("initial")

    val baseHash = git(project, "rev-parse HEAD").trim()
    val message = "${baseHash.take(hashLength)} custom message"
    echo("a.txt", "changed")
    stash(message)

    val stash = loadStashStack(project, projectRoot).single()
    assertThat(stash.branch).isEqualTo("On master")
    assertThat(stash.subject).isEqualTo(message)
  }

  @Test
  fun `test stash subject keeps a nonmatching hash`() {
    val message = "deadbeef custom message"
    val stash = StashInfo(context.projectRoot, HashImpl.build("0".repeat(40)), HashImpl.build("a".repeat(40)), emptyList(), 0,
                          "stash@{0}", "WIP on master", message)

    assertThat(stash.subject).isEqualTo(message)
  }

  @ParameterizedTest
  @ValueSource(strings = ["deadbeef", "deadbeef ", ""])
  fun `test stash subject keeps a hash without following message`(message: String) {
    val stash = StashInfo(context.projectRoot, HashImpl.build("0".repeat(40)), HashImpl.build("deadbeef".repeat(5)), emptyList(), 0,
                          "stash@{0}", "WIP on master", message)

    assertThat(stash.subject).isEqualTo(message)
  }

  @Test
  fun `test default stash subject on detached head`(): Unit = with(context) {
    touch("a.txt")
    add()
    commit("initial")
    git(project, "checkout --detach HEAD")

    echo("a.txt", "changed")
    stash()

    val stash = loadStashStack(project, projectRoot).single()
    assertStashInfoIsCorrect(0, "initial", "(no branch)", stash)
  }

  private fun GitSingleRepoContext.assertStashInfoIsCorrect(expectedNumber: Int,
                                                            expectedMessage: String,
                                                            expectedBranch: String,
                                                            actualStash: StashInfo) {
    assertThat(actualStash.stash).isEqualTo("stash@{$expectedNumber}")
    assertStashMessageEquals(expectedMessage, actualStash)
    assertThat(actualStash.branch).isEqualTo("WIP on $expectedBranch")
    assertThat(actualStash.subject).isEqualTo(expectedMessage)
    assertThat(actualStash.authorTime).isEqualTo(stashAuthorTime(expectedNumber))
  }

  private fun assertStashMessageEquals(expectedMessage: String, stash: StashInfo) {
    assertThat(stashMessagePattern(expectedMessage).matcher(stash.message).matches())
      .describedAs("Expected '<HASH> $expectedMessage', got '${stash.message}'")
      .isTrue()
  }

  private fun stashMessagePattern(commitMessage: String) = Pattern.compile("${GitUtil.HASH_REGEX.pattern()} ${commitMessage}")

  private fun GitSingleRepoContext.stash(message: String? = null) =
    if (message == null) git(project, "stash") else git(project, "stash push --message '$message'")

  private fun GitSingleRepoContext.stashAuthorTime(stashNumber: Int): Long {
    val noWalkParameter = GitLogUtil.getNoWalkParameter(project)
    val timeString = git(project, "log --pretty=format:%at $noWalkParameter stash@{$stashNumber}")
    return GitLogUtil.parseTime(timeString)
  }
}