// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log.squash

import git4idea.test.*

class GitSquashOperationTest : GitSingleRepoTest() {
  fun `test squash last few commits`() {
    val commitA = file("a").create().addCommit("Commit a").details()
    val commitB = file("b").create().addCommit("Commit b").details()
    val commitC = file("c").create().addCommit("Commit c").details()
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"

    GitSquashOperation(repo).execute(commitsToSquash, newMessage)

    assertLastMessage(newMessage)
    repo.assertCommitted {
      added("a")
      added("b")
      added("c")
    }
  }

  fun `test squash few non-last commits`() {
    file("before").create().addCommit("Commit before")
    val commitA = file("a").create().addCommit("Commit a").details()
    val commitB = file("b").create().addCommit("Commit b").details()
    val commitC = file("c").create().addCommit("Commit c").details()
    file("after").create().addCommit("Commit after")
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"
    GitSquashOperation(repo).execute(commitsToSquash, newMessage)

    assertMessage(newMessage, repo.message("HEAD^"))
    repo.assertCommitted(1) {
      added("after")
    }
    repo.assertCommitted(2) {
      added("a")
      added("b")
      added("c")
    }
    repo.assertCommitted(3) {
      added("before")
    }
  }

  fun `test squash non-linear history`() {
    val commitA = file("a").create().addCommit("Commit a").details()
    file("between1").create().addCommit("Commit between1")
    val commitB = file("b").create().addCommit("Commit b").details()
    file("between2").create().addCommit("Commit between2")
    val commitC = file("c").create().addCommit("Commit c").details()
    val commitsToSquash = listOf(commitC, commitB, commitA)

    refresh()
    updateChangeListManager()

    val newMessage = "Squashed commit message"
    GitSquashOperation(repo).execute(commitsToSquash, newMessage)

    assertMessage(newMessage, repo.message("HEAD~2"))
    repo.assertCommitted(1) {
      added("between2")
    }
    repo.assertCommitted(2) {
      added("between1")
    }
    repo.assertCommitted(3) {
      added("a")
      added("b")
      added("c")
    }
  }
}