// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.vcsTestProjectPathFixture
import git4idea.config.GitSaveChangesPolicy
import git4idea.test.GitPlatformTestContext
import git4idea.test.createRepository
import git4idea.test.gitPlatformContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitWorktreeSupportStatusTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  @Test
  fun `test returns unsupported when project has no repositories`(): Unit = with(context) {
    assertThat(GitWorkingTreesService.getWorktreeSupportStatus(project)).isEqualTo(GitWorktreeSupportStatus.Unsupported)
  }

  @Test
  fun `test returns single repository status for single repository project`(): Unit = with(context) {
    val repository = createRepository(project, projectNioRoot, true)

    val status = GitWorkingTreesService.getWorktreeSupportStatus(project)

    assertThat(status).isEqualTo(GitWorktreeSupportStatus.SingleRepository(repository))
  }

  @Test
  fun `test returns multiple repository status for multi repository project`(): Unit = with(context) {
    val firstRepository = createRepository(project, projectNioRoot, true)
    val secondRepository = createRepository(project, testNioRoot.resolve("community"), true)

    val status = GitWorkingTreesService.getWorktreeSupportStatus(project)

    assertThat(status).isInstanceOf(GitWorktreeSupportStatus.MultipleRepository::class.java)
    val multipleRepositoryStatus = status as GitWorktreeSupportStatus.MultipleRepository
    assertThat(multipleRepositoryStatus.repositories).containsExactlyInAnyOrder(firstRepository, secondRepository)
  }

  @Test
  fun `test worktree creation supported for single repository`(): Unit = with(context) {
    val repository = createRepository(project, projectNioRoot, true)

    assertThat(GitWorkingTreesService.isWorktreeCreationSupported(repository)).isTrue()
  }

  @Test
  fun `test worktree creation not supported for multi repository`(): Unit = with(context) {
    val firstRepository = createRepository(project, projectNioRoot, true)
    createRepository(project, testNioRoot.resolve("community"), true)

    assertThat(GitWorkingTreesService.isWorktreeCreationSupported(firstRepository)).isFalse()
  }

  // the feature flag is read in the test body, so overriding it for the invocation is enough
  @Test
  @RegistryKey("git.enable.working.trees.feature", "false")
  fun `test worktree creation not supported when feature disabled`(): Unit = with(context) {
    val repository = createRepository(project, projectNioRoot, true)

    assertThat(GitWorkingTreesService.isWorktreeCreationSupported(repository)).isFalse()
  }
}
