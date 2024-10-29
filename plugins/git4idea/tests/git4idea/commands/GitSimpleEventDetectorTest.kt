// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import git4idea.cherrypick.GitLocalChangesConflictDetector
import git4idea.test.GitSingleRepoTest

class GitSimpleEventDetectorTest: GitSingleRepoTest() {
  val eventDetector = GitLocalChangesConflictDetector()

  fun `test local changes would be overwritten by cherry-pick`() {
    val file = file("test")
    val commit = file.create("initial\n").addCommit("initial").hash()
    file("new").append("new-2\n").add()

    val result = Git.getInstance().cherryPick(repo, commit, true, false, eventDetector)
    assertResult(result, eventDetector)
  }

  fun `test local changes would be overwritten by merge`() {
    val file = file("test")
    val commit = file.create("initial\n").addCommit("initial").hash()
    file.write("new").addCommit("new")
    file.append("more changes")

    val result = Git.getInstance().cherryPick(repo, commit, true, false, eventDetector)
    assertResult(result, eventDetector)
    assertTrue(eventDetector.byMerge)
  }

  fun `test local changes would be overwritten by revert`() {
    val file = file("test")
    val commit = file.create("initial\n").addCommit("initial").hash()
    file("new").append("new-2\n").add()

    val result = Git.getInstance().revert(repo, commit, true, eventDetector)
    assertResult(result, eventDetector)
  }

  private fun assertResult(result: GitCommandResult, eventDetector: GitLocalChangesConflictDetector) {
    assertFalse("Output - ${result.errorOutputAsJoinedString}", result.success())
    assertTrue("Event wasn't detected - ${result.errorOutputAsJoinedString}", eventDetector.isDetected)
  }
}