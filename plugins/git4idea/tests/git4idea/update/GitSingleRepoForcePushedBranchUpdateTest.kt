// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.actions.branch.GitForcePushedBranchUpdateExecutor
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.cd
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.makeCommit
import git4idea.test.setupRepositories
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
internal class GitSingleRepoForcePushedBranchUpdateTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private lateinit var repository: GitRepository
  private lateinit var parentRepo: Path
  private lateinit var broRepo: Path

  @BeforeEach
  fun setUp() {
    with(context) {
      val trinity = setupRepositories(projectPath, "parent", "bro")

      cd(projectPath)
      refresh()
      repositoryManager.updateAllRepositories()

      repository = trinity.projectRepo
      parentRepo = trinity.parent
      broRepo = trinity.bro
    }
  }

  @Test
  fun `test single repo update`(): Unit = with(context) {
    cd(broRepo)
    makeCommit("bro.txt")
    git("push -f")

    repository.assertNotExists("bro.txt")

    cd(repository)
    makeCommit("localFile1.txt")
    makeCommit("localFile2.txt")

    val commitsBeforeUpdate = repository.commitsFrom("origin/master..master")
    assertThat(commitsBeforeUpdate).hasSize(2)

    updateChangeListManager()

    val updateExecutor = project.service<GitForcePushedBranchUpdateExecutor>()
    updateExecutor.updateCurrentBranch()
    updateExecutor.waitForUpdate()

    val commitsAfterUpdate = repository.commitsFrom("origin/master..master")
    assertThat(commitsAfterUpdate).hasSize(2)

    repository.assertExists("bro.txt")
    repository.assertExists("localFile1.txt")
    repository.assertExists("localFile2.txt")
    assertThat(repository.branches.localBranches).hasSize(1)
    assertNotificationByMessage(GitBundle.message("action.git.update.force.pushed.branch.success"))
  }

  @Test
  fun `test repo update with local merge commit`(): Unit = with(context) {
    cd(broRepo)
    makeCommit("bro.txt")
    git("push -f")

    repository.assertNotExists("bro.txt")

    cd(repository)
    makeCommit("localFile1.txt")
    git("checkout -b feature")
    makeCommit("localFileToMerge.txt")
    git("checkout master")
    makeCommit("localFile2.txt")
    git("merge feature")

    val commitsBeforeUpdate = repository.commitsFrom("origin/master..master")
    assertThat(commitsBeforeUpdate).hasSize(4)
    assertThat(commitsBeforeUpdate.first().isMergeCommit).isTrue()

    updateChangeListManager()

    val updateExecutor = project.service<GitForcePushedBranchUpdateExecutor>()
    updateExecutor.updateCurrentBranch()
    updateExecutor.waitForUpdate()

    val commitsAfterUpdate = repository.commitsFrom("origin/master..master")
    assertThat(commitsAfterUpdate).hasSize(5)
    assertThat(commitsAfterUpdate.first().isMergeCommit).isTrue()

    repository.assertExists("bro.txt")
    repository.assertExists("localFile1.txt")
    repository.assertExists("localFile2.txt")
    repository.assertExists("localFileToMerge.txt")
    assertThat(repository.branches.localBranches).hasSize(2)
    assertNotificationByMessage(GitBundle.message("action.git.update.force.pushed.branch.success"))
  }

  private val VcsCommitMetadata.isMergeCommit get() = parents.size == 2
}
