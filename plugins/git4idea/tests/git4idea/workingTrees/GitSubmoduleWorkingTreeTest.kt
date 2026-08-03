// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.vcs.test.refresh
import git4idea.GitWorkingTree
import git4idea.actions.ref.GitSingleRefAction
import git4idea.config.GitSaveChangesPolicy
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.git
import git4idea.test.registerRepo
import git4idea.test.setupDefaultUsername
import git4idea.update.addSubmodule
import git4idea.update.createPlainRepo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitSubmoduleWorkingTreeTest {
  private val contextFixture = submoduleWorkingTreeFixture()
  private val context: GitSubmoduleWorkingTreeContext get() = contextFixture.get()

  @Test
  fun `test submodule main worktree is recognized as current`(): Unit = with(context) {
    sub.ensureWorkingTreesUpToDateForTests()

    assertThat(sub.workingTreeHolder.getWorkingTrees()).containsExactlyInAnyOrderElementsOf(
      listOf(GitWorkingTree(sub.root.path, sub.currentBranch!!.fullName, true, true))
    )
  }

  @Test
  @RegistryKey("git.enable.working.trees.feature", "true")
  fun `test branch is not reported as checked out in another worktree`(): Unit = with(context) {
    sub.ensureWorkingTreesUpToDateForTests()

    val branch = sub.currentBranch!!
    assertThat(GitSingleRefAction.getWorkingTreeWithRef(branch, sub, skipCurrentWorkingTree = true))
      .describedAs("Submodule branch must not be reported as checked out in another worktree")
      .isNull()
  }
}

internal interface GitSubmoduleWorkingTreeContext : GitPlatformTestContext {
  /** The submodule repository of the project. */
  val sub: GitRepository
}

private fun submoduleWorkingTreeFixture(): TestFixture<GitSubmoduleWorkingTreeContext> =
  gitWorkingTreePlatformFixture(saveChangesPolicy = GitSaveChangesPolicy.STASH).submoduleFixture()

/**
 * Prepares a remote main repository with a `sub` submodule, clones it into the project directory
 * and registers both repositories.
 */
private fun TestFixture<GitPlatformTestContext>.submoduleFixture(): TestFixture<GitSubmoduleWorkingTreeContext> = testFixture {
  val platformContext = init()
  with(platformContext) {
    // prepare remote main + remote sub, add the submodule to main
    val mainRemote = createPlainRepo(project, testNioRoot, "main")
    val subRemote = createPlainRepo(project, testNioRoot, "sub")
    addSubmodule(project, mainRemote.local, subRemote.remote, "sub")

    // clone the main project with the submodule into the project directory
    cd(testNioRoot)
    git("clone --recurse-submodules ${mainRemote.remote} maintmp")
    FileUtil.moveDirWithContent(testNioRoot.resolve("maintmp").toFile(), projectRoot.toNioPath().toFile())
    cd(projectRoot)
    setupDefaultUsername()

    val subRoot = projectNioRoot.resolve("sub")
    cd(subRoot)
    setupDefaultUsername()
    git("checkout master") // a submodule is checked out in detached HEAD by default

    refresh()
    registerRepo(project, projectNioRoot)
    val sub = registerRepo(project, subRoot)
    sub.update()

    val result = object : GitSubmoduleWorkingTreeContext, GitPlatformTestContext by platformContext {
      override val sub = sub
    }
    initialized(result) {}
  }
}
