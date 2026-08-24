// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.test

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.vcs.test.vcsPlatformFixture
import com.intellij.vcs.test.vcsTestProjectPathFixture
import git4idea.config.GitSaveChangesPolicy
import java.nio.file.Path

/**
 * A project opened in `<testNioRoot>/project` with the Git test infrastructure set up, but without any registered
 * repository. Use [gitSingleRepoContextFixture] if the project root itself should be a repository.
 *
 * Pass a custom [projectPathFixture] if the project directory (and the repository in it) has to be prepared on disk
 * before the project is opened, e.g. for a clone or a linked working tree.
 */
fun gitPlatformContextFixture(
  projectPathFixture: TestFixture<Path> = vcsTestProjectPathFixture(),
  saveChangesPolicy: GitSaveChangesPolicy = GitSaveChangesPolicy.SHELVE,
  hasRemoteGitOperation: Boolean = false,
  initCommitContext: CommitContext.() -> Unit = {},
): TestFixture<GitPlatformTestContext> {
  val projectFixture: TestFixture<Project> = projectFixture(projectPathFixture, openAfterCreation = true)
  return projectFixture.vcsPlatformFixture()
    .gitPlatformFixture(projectFixture, saveChangesPolicy, hasRemoteGitOperation, initCommitContext)
}

/**
 * Same as [gitPlatformContextFixture], plus a Git repository created in the project root.
 */
fun gitSingleRepoContextFixture(
  projectPathFixture: TestFixture<Path> = vcsTestProjectPathFixture(),
  saveChangesPolicy: GitSaveChangesPolicy = GitSaveChangesPolicy.SHELVE,
  hasRemoteGitOperation: Boolean = false,
  makeInitialCommit: Boolean = true,
  initCommitContext: CommitContext.() -> Unit = {},
): TestFixture<GitSingleRepoContext> =
  gitPlatformContextFixture(projectPathFixture, saveChangesPolicy, hasRemoteGitOperation, initCommitContext)
    .gitSingleRepoFixture(makeInitialCommit)
