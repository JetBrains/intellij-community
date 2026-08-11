// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitUtil
import git4idea.branch.GitBranchesCollection
import git4idea.config.GitVersion
import git4idea.test.GitPlatformTestContext
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.registerRepo
import git4idea.test.setupDefaultUsername
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import java.nio.file.Path

private val WORKTREE_SUPPORT_VERSION = GitVersion(2, 5, 0, 0)

@TestApplication
abstract class GitWorkTreeBaseTest {

  private val contextFixture = gitPlatformContextFixture()
  protected val context: GitPlatformTestContext get() = contextFixture.get()

  protected lateinit var myMainRoot: Path
  protected lateinit var myRepo: GitRepository

  @BeforeEach
  fun setUp() {
    with(context) {
      Assumptions.assumeTrue(vcs.version.isLaterOrEqual(WORKTREE_SUPPORT_VERSION), "Worktrees are not supported in ${vcs.version}")

      cd(testNioRoot)
      myMainRoot = initMainRepo()

      cd(myMainRoot)
      git("worktree add $projectPath")
      checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(projectNioRoot.resolve(GitUtil.DOT_GIT))) {
        "'git worktree add' didn't create ${GitUtil.DOT_GIT} in $projectPath"
      }

      myRepo = registerRepo(project, projectNioRoot)
      setupDefaultUsername()
      assertThat(repositoryManager.repositories).hasSize(1)
      assertThat(repositoryManager.getRepositoryForRoot(projectRoot)).isNotNull()
    }
  }

  protected abstract fun GitPlatformTestContext.initMainRepo(): Path
}

internal fun assertBranchHash(expectedHash: String, branches: GitBranchesCollection, branchName: String) {
  val branch = branches.findBranchByName(branchName)
  assertThat(branch).describedAs("Branch $branchName not found").isNotNull()
  assertThat(branches.getHash(branch!!)?.asString()).describedAs("Hash of $branchName is incorrect").isEqualTo(expectedHash)
}
