// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.vcs.test.refresh
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.git
import git4idea.test.registerRepo
import git4idea.test.setupDefaultUsername
import java.nio.file.Path

/**
 * A project which is a clone of a repository with a `sub` submodule.
 */
internal interface GitSubmoduleProjectContext : GitPlatformTestContext {
  /** The main repository of the project. */
  val main: GitRepository

  /** The submodule repository inside the project. */
  val sub: GitRepository

  /** The second clone of the main repository, outside of the project. */
  val main2: RepositoryAndParent

  /** The submodule inside [main2]. */
  val sub2: Path
}

/**
 * Prepares a remote main repository with a `sub` submodule, clones it into the project directory
 * and registers both repositories.
 *
 * By default the submodule is left in the detached HEAD state it is cloned in. Pass [checkoutSubmoduleBranch] to check
 * out `master` in it instead.
 */
internal fun TestFixture<GitPlatformTestContext>.gitSubmoduleProjectFixture(
  checkoutSubmoduleBranch: Boolean = false,
  updateSubmoduleRepo: Boolean = false,
): TestFixture<GitSubmoduleProjectContext> = testFixture {
  val platformContext = init()
  with(platformContext) {
    // prepare remote main + remote sub, add the submodule to main
    val mainRemote = createPlainRepo(project, testNioRoot, "main")
    val subRemote = createPlainRepo(project, testNioRoot, "sub")
    val subRemoteRoot = addSubmodule(project, mainRemote.local, subRemote.remote, "sub")

    // clone the main project with the submodule into the project directory
    cd(testNioRoot)
    git("clone --recurse-submodules ${mainRemote.remote} maintmp")
    FileUtil.moveDirWithContent(testNioRoot.resolve("maintmp").toFile(), projectRoot.toNioPath().toFile())
    cd(projectRoot)
    setupDefaultUsername()

    val subRoot = projectNioRoot.resolve("sub")
    cd(subRoot)
    setupDefaultUsername()
    if (checkoutSubmoduleBranch) {
      git("checkout master") // a submodule is checked out in detached HEAD by default
    }

    refresh()
    val main = registerRepo(project, projectNioRoot)
    val sub = registerRepo(project, subRoot)
    if (updateSubmoduleRepo) {
      sub.update()
    }

    val result = object : GitSubmoduleProjectContext, GitPlatformTestContext by platformContext {
      override val main = main
      override val sub = sub
      override val main2 = mainRemote
      override val sub2 = subRemoteRoot
    }
    initialized(result) {}
  }
}
