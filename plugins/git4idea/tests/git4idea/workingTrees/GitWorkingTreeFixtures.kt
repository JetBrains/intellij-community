// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.vcs.test.vcsPlatformFixture
import com.intellij.vcs.test.vcsTestProjectPathFixture
import git4idea.GitLocalBranch
import git4idea.config.GitSaveChangesPolicy
import git4idea.repo.GitRepository
import git4idea.repo.GitWorkingTreeHolderImpl
import git4idea.test.GitPlatformTestContext
import git4idea.test.GitSingleRepoContext
import git4idea.test.branch
import git4idea.test.gitExistingSingleRepoFixture
import git4idea.test.gitPlatformFixture
import git4idea.test.gitSingleRepoFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Path

/**
 * A project in `<testNioRoot>/project` with a Git repository created in the project root.
 */
internal fun gitWorkingTreeSingleRepoFixture(): TestFixture<GitSingleRepoContext> =
  gitWorkingTreePlatformFixture().gitSingleRepoFixture(makeInitialCommit = true)

/**
 * A project whose directory (and Git repository in it) is prepared by [projectPathFixture] before the project
 * is opened, e.g. a clone or a linked working tree. The repository is only registered as a VCS root.
 */
internal fun gitWorkingTreeExistingRepoFixture(projectPathFixture: TestFixture<Path>): TestFixture<GitSingleRepoContext> =
  gitWorkingTreePlatformFixture(projectPathFixture).gitExistingSingleRepoFixture()

/**
 * A project in `<testNioRoot>/project` without any registered repository.
 */
internal fun gitWorkingTreePlatformFixture(
  projectPathFixture: TestFixture<Path> = vcsTestProjectPathFixture(),
  saveChangesPolicy: GitSaveChangesPolicy = GitSaveChangesPolicy.SHELVE,
): TestFixture<GitPlatformTestContext> {
  val projectFixture: TestFixture<Project> = projectFixture(projectPathFixture, openAfterCreation = true)
  return projectFixture.vcsPlatformFixture()
    .gitPlatformFixture(projectFixture, defaultSaveChangesPolicy = saveChangesPolicy, hasRemoteGitOperation = false)
}

internal fun GitRepository.ensureWorkingTreesUpToDateForTests() {
  runBlocking {
    withContext(Dispatchers.IO) {
      (workingTreeHolder as GitWorkingTreeHolderImpl).updateState()
    }
  }
}

internal fun createBranch(repo: GitRepository, branchName: String): GitLocalBranch {
  repo.branch(branchName)
  repo.update()
  val newBranch = repo.branches.findLocalBranch(branchName)
  assertThat(newBranch).describedAs("Branch $branchName was not created").isNotNull()
  return newBranch!!
}
