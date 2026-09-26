// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import com.intellij.testFramework.junit5.TestApplication
import git4idea.cherrypick.GitLocalChangesConflictDetector
import git4idea.test.GitSingleRepoContext
import git4idea.test.file
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class GitSimpleEventDetectorTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  private val eventDetector = GitLocalChangesConflictDetector()

  @Test
  fun `test local changes would be overwritten by cherry-pick`(): Unit = with(context) {
    val file = file("test")
    val commit = file.create("initial\n").addCommit("initial").hash()
    file("new").append("new-2\n").add()

    val result = Git.getInstance().cherryPick(repo, listOf(commit), true, false, eventDetector)
    assertResult(result, eventDetector)
  }

  @Test
  fun `test local changes would be overwritten by merge`(): Unit = with(context) {
    val file = file("test")
    val commit = file.create("initial\n").addCommit("initial").hash()
    file.write("new").addCommit("new")
    file.append("more changes")

    val result = Git.getInstance().cherryPick(repo, listOf(commit), true, false, eventDetector)
    assertResult(result, eventDetector)
    assertThat(eventDetector.byMerge).isTrue()
  }

  @Test
  fun `test local changes would be overwritten by revert`(): Unit = with(context) {
    val file = file("test")
    val commit = file.create("initial\n").addCommit("initial").hash()
    file("new").append("new-2\n").add()

    val result = Git.getInstance().revert(repo, commit, true, eventDetector)
    assertResult(result, eventDetector)
  }

  private fun assertResult(result: GitCommandResult, eventDetector: GitLocalChangesConflictDetector) {
    assertThat(result.success()).describedAs("Output: ${result.errorOutputAsJoinedString}").isFalse()
    assertThat(eventDetector.isDetected).describedAs("Event was not detected: ${result.errorOutputAsJoinedString}").isTrue()
  }
}