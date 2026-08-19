// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import git4idea.GitUtil
import git4idea.branch.GitBranchesCollection
import git4idea.config.GitVersion
import git4idea.test.GitPlatformTestContext
import git4idea.test.git
import git4idea.test.registerRepo
import git4idea.test.setupDefaultUsername
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path

private val WORKTREE_SUPPORT_VERSION = GitVersion(2, 5, 0, 0)

internal interface GitWorkTreeContext : GitPlatformTestContext {
  /** The repository the project working tree is linked to. It lives in [testNioRoot], outside of the project. */
  val mainRoot: Path

  /** The repository of the linked working tree, which is the project root. */
  val repo: GitRepository
}

/**
 * A project whose root is a linked working tree of the repository prepared by [initMainRepo] outside of the project.
 *
 * The test is skipped if the Git version at hand doesn't support working trees.
 */
internal fun TestFixture<GitPlatformTestContext>.gitWorkTreeFixture(
  initMainRepo: GitPlatformTestContext.() -> Path,
): TestFixture<GitWorkTreeContext> = testFixture {
  val platformContext = init()
  with(platformContext) {
    assumeTrue(vcs.version.isLaterOrEqual(WORKTREE_SUPPORT_VERSION), "Worktrees are not supported in ${vcs.version}")

    cd(testNioRoot)
    val mainRoot = initMainRepo()

    cd(mainRoot)
    git("worktree add $projectPath")
    checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectNioRoot.resolve(GitUtil.DOT_GIT))) {
      "'git worktree add' didn't create ${GitUtil.DOT_GIT} in $projectPath"
    }

    val repo = registerRepo(project, projectNioRoot)
    setupDefaultUsername()
    assertThat(repositoryManager.repositories).hasSize(1)
    assertThat(repositoryManager.getRepositoryForRoot(projectRoot)).isNotNull()

    val result = object : GitWorkTreeContext, GitPlatformTestContext by platformContext {
      override val mainRoot = mainRoot
      override val repo = repo
    }
    initialized(result) {}
  }
}

internal fun assertBranchHash(expectedHash: String, branches: GitBranchesCollection, branchName: String) {
  val branch = branches.findBranchByName(branchName)
  assertThat(branch).describedAs("Branch $branchName not found").isNotNull()
  assertThat(branches.getHash(branch!!)?.asString()).describedAs("Hash of $branchName is incorrect").isEqualTo(expectedHash)
}
