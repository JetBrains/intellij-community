// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.dvcs.DvcsUtil.getPushSupport
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.vcs.test.refresh
import git4idea.GitBranch
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.gitPlatformContextFixture
import git4idea.test.setupRepositories
import git4idea.update.GitUpdateResult
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path

internal interface GitPushContext : GitPlatformTestContext {
  val pushSupport: GitPushSupport
}

// ---------------------------------------------------------------------------------------------------------------
// A single repository in the project root, with a bare parent and a "bro" clone outside of the project
// ---------------------------------------------------------------------------------------------------------------

internal interface GitPushSingleRepoContext : GitPushContext {
  val repository: GitRepository

  /** The bare repository which both [repository] and [broRepo] push to. */
  val parentRepo: Path

  /** Another clone of [parentRepo], outside of the project. */
  val broRepo: Path
}

internal fun gitPushSingleRepoFixture(): TestFixture<GitPushSingleRepoContext> = testFixture {
  val platformContext = gitPlatformContextFixture().init()
  with(platformContext) {
    val trinity = setupRepositories(projectPath, "parent", "bro")

    cd(projectPath)
    refresh()
    updateRepositories()

    val result = object : GitPushSingleRepoContext, GitPlatformTestContext by platformContext {
      override val pushSupport = gitPushSupport()
      override val repository = trinity.projectRepo
      override val parentRepo = trinity.parent
      override val broRepo = trinity.bro
    }
    initialized(result) {}
  }
}

internal fun GitPlatformTestContext.gitPushSupport(): GitPushSupport = getPushSupport(vcs) as GitPushSupport

internal fun GitPlatformTestContext.updateRepositories() {
  repositoryManager.updateAllRepositories()
  VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
  changeListManager.ensureUpToDate()
}

internal fun assertRepoResult(
  type: GitPushRepoResult.Type,
  pushedCommits: Int,
  from: String,
  to: String,
  updateResult: GitUpdateResult?,
  actualResult: GitPushRepoResult,
) {
  val message = "Result is incorrect: $actualResult"
  assertThat(actualResult.type).describedAs(message).isEqualTo(type)
  assertThat(actualResult.numberOfPushedCommits).describedAs(message).isEqualTo(pushedCommits)
  assertThat(actualResult.sourceBranch).describedAs(message).isEqualTo(GitBranch.REFS_HEADS_PREFIX + from)
  assertThat(actualResult.targetBranch).describedAs(message).isEqualTo(GitBranch.REFS_REMOTES_PREFIX + to)
  if (updateResult != null) {
    assertThat(actualResult.updateResult).describedAs(message).isEqualTo(updateResult)
  }
}
