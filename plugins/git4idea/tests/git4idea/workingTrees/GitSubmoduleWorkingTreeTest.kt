// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.Executor.cd
import git4idea.GitWorkingTree
import git4idea.actions.ref.GitSingleRefAction
import git4idea.repo.GitRepository
import git4idea.repo.GitWorkingTreeHolderImpl
import git4idea.test.git
import git4idea.test.registerRepo
import git4idea.test.setupDefaultUsername
import git4idea.update.GitSubmoduleTestBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class GitSubmoduleWorkingTreeTest : GitSubmoduleTestBase() {
  private lateinit var sub: GitRepository

  override fun setUp() {
    super.setUp()

    // prepare remote main + remote sub, add the submodule to main
    val mainRemote = createPlainRepo("main")
    val subRemote = createPlainRepo("sub")
    addSubmodule(mainRemote.local, subRemote.remote, "sub")

    // clone the main project with the submodule into the project directory
    cd(testNioRoot)
    git("clone --recurse-submodules ${mainRemote.remote} maintmp")
    FileUtil.moveDirWithContent(testNioRoot.resolve("maintmp").toFile(), projectRoot.toNioPath().toFile())
    cd(projectRoot)
    setupDefaultUsername()

    val subFile = projectNioRoot.resolve("sub")
    cd(subFile)
    setupDefaultUsername()
    git("checkout master") // a submodule is checked out in detached HEAD by default

    refresh()
    registerRepo(project, projectNioRoot)
    sub = registerRepo(project, subFile)
    sub.update()
  }

  fun `test submodule main worktree is recognized as current`() {
    sub.ensureWorkingTreesUpToDate()

    assertSameElements(
      sub.workingTreeHolder.getWorkingTrees(),
      listOf(GitWorkingTree(sub.root.path, sub.currentBranch!!.fullName, true, true)),
    )
  }

  fun `test branch is not reported as checked out in another worktree`() {
    Registry.get("git.enable.working.trees.feature").setValue(true, testRootDisposable)
    sub.ensureWorkingTreesUpToDate()

    val branch = sub.currentBranch!!
    assertNull(
      "Submodule branch must not be reported as checked out in another worktree",
      GitSingleRefAction.getWorkingTreeWithRef(branch, sub, skipCurrentWorkingTree = true),
    )
  }

  private fun GitRepository.ensureWorkingTreesUpToDate() {
    runBlocking {
      withContext(Dispatchers.IO) {
        (workingTreeHolder as GitWorkingTreeHolderImpl).updateState()
      }
    }
  }
}
