// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.util.registry.Registry
import git4idea.test.GitPlatformTest
import git4idea.test.createRepository

class GitWorktreeSupportStatusTest : GitPlatformTest() {

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    Registry.get("git.enable.working.trees.feature").setValue(true, testRootDisposable)
  }

  fun `test returns unsupported when project has no repositories`() {
    assertEquals(GitWorktreeSupportStatus.Unsupported, GitWorkingTreesService.getWorktreeSupportStatus(project))
  }

  fun `test returns single repository status for single repository project`() {
    val repository = createRepository(project, projectNioRoot, true)

    val status = GitWorkingTreesService.getWorktreeSupportStatus(project)

    assertEquals(GitWorktreeSupportStatus.SingleRepository(repository), status)
  }

  fun `test returns multiple repository status for multi repository project`() {
    val firstRepository = createRepository(project, projectNioRoot, true)
    val secondRepository = createRepository(project, testNioRoot.resolve("community"), true)

    val status = GitWorkingTreesService.getWorktreeSupportStatus(project)

    assertTrue(status is GitWorktreeSupportStatus.MultipleRepository)
    val multipleRepositoryStatus = status as GitWorktreeSupportStatus.MultipleRepository
    assertSameElements(multipleRepositoryStatus.repositories, listOf(firstRepository, secondRepository))
  }
}
