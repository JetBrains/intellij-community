// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.actions.branch.GitForcePushedBranchUpdateExecutor
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.test.*
import java.nio.file.Path

class GitSingleRepoForcePushedBranchUpdateTest : GitForcePushedBranchUpdateBaseTest() {

  private lateinit var repository: GitRepository
  private lateinit var parentRepo: Path
  private lateinit var broRepo: Path

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    val trinity = setupRepositories(projectPath, "parent", "bro")
    parentRepo = trinity.parent
    broRepo = trinity.bro
    repository = trinity.projectRepo

    cd(projectPath)
    refresh()
    repositoryManager.updateAllRepositories()
  }

  fun `test single repo update`() {
    cd(broRepo)
    makeCommit("bro.txt")
    git("push -f")

    repository.assertNotExists("bro.txt")

    cd(repository)
    makeCommit("localFile1.txt")
    makeCommit("localFile2.txt")

    val commitsBeforeUpdate = repository.commitsFrom("origin/master..master")
    assertTrue(commitsBeforeUpdate.size == 2)

    updateChangeListManager()

    val updateExecutor = project.service<GitForcePushedBranchUpdateExecutor>()
    updateExecutor.updateCurrentBranch()
    updateExecutor.waitForUpdate()

    val commitsAfterUpdate = repository.commitsFrom("origin/master..master")
    assertTrue(commitsAfterUpdate.size == 2)

    repository.assertExists("bro.txt")
    repository.assertExists("localFile1.txt")
    repository.assertExists("localFile2.txt")
    assertTrue(repository.branches.localBranches.size == 1)
    assertNotificationByMessage(GitBundle.message("action.git.update.force.pushed.branch.success"))
  }

  fun `test repo update with local merge commit`() {
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
    assertTrue(commitsBeforeUpdate.size == 4)
    assertTrue(commitsBeforeUpdate.first().isMergeCommit)

    updateChangeListManager()

    val updateExecutor = project.service<GitForcePushedBranchUpdateExecutor>()
    updateExecutor.updateCurrentBranch()
    updateExecutor.waitForUpdate()

    val commitsAfterUpdate = repository.commitsFrom("origin/master..master")
    assertTrue(commitsAfterUpdate.size == 5)
    assertTrue(commitsAfterUpdate.first().isMergeCommit)

    repository.assertExists("bro.txt")
    repository.assertExists("localFile1.txt")
    repository.assertExists("localFile2.txt")
    repository.assertExists("localFileToMerge.txt")
    assertTrue(repository.branches.localBranches.size == 2)
    assertNotificationByMessage(GitBundle.message("action.git.update.force.pushed.branch.success"))
  }

  private val VcsCommitMetadata.isMergeCommit get() = parents.size == 2
}
