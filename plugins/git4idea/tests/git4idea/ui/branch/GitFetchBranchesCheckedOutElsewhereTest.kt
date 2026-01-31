// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.vcs.Executor.cd
import git4idea.i18n.GitBundle
import git4idea.repo.GitWorkTreeBaseTest
import git4idea.test.assertCurrentRevision
import git4idea.test.checkout
import git4idea.test.git
import git4idea.test.initRepo
import git4idea.test.tac
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path

class GitFetchBranchesCheckedOutElsewhereTest : GitWorkTreeBaseTest() {
  lateinit var bro: Path
  lateinit var parent: Path

  override fun initMainRepo(): Path {
    val mainDir = testNioRoot.resolve("main")
    Files.createDirectories(mainDir)
    initRepo(project, mainDir, true)
    return mainDir
  }

  override fun setUp() {
    super.setUp()
    parent = prepareRemoteRepo(myRepo)
    bro = createBroRepo("bro", parent)
  }

  fun `test fetch blocked by checked out branch then retries with update-head-ok flag`() {
    cd(myMainRoot)
    git("checkout -b feature")
    tac("a.txt", "main")
    git("push -u origin feature")

    cd(bro)
    git("fetch origin feature")
    git("checkout feature")
    val broCommit = tac("b.txt", "bro")
    git("push -u origin feature")

    myRepo.update()

    val fetchTargets = prepareUpdateTargets(listOf(myRepo), listOf("feature")).fetchTargets
    runBlocking { fetchBranches(project, fetchTargets, updateHeadOk = false) }

    val title = GitBundle.message("branches.update.failed")
    val message = GitBundle.message("branches.update.error.branch.checked.out", "feature", getPresentablePath(myMainRoot.toString()))
    assertErrorNotification(title, message, actions = listOf(
      GitBundle.message("branches.update.anyway.notification.action")
    ))

    runBlocking { fetchBranches(project, fetchTargets, updateHeadOk = true) }

    assertSuccessfulNotification(GitBundle.message("branches.fetch.finished", 1))

    cd(myMainRoot)
    checkout("-b another")
    myRepo.checkout("feature")
    myRepo.assertCurrentRevision(broCommit)
  }
}
