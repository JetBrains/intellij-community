// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.Executor.touch
import git4idea.config.GitVersion
import git4idea.test.GitSingleRepoTest
import git4idea.test.git
import git4idea.test.makeCommit
import git4idea.test.setupDefaultUsername
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path

class GitRecentProjectsBranchesServiceReftableTest : GitSingleRepoTest() {

  override fun setUp() {
    super.setUp()
    assumeTrue("Refs command is supported only since Git 2.46", isVersionSupported())
  }

  private fun isVersionSupported(): Boolean {
    return vcs.version.isLaterOrEqual(GitVersion(2, 46, 0, 0))
  }

  fun `test branch is resolved in case of reftable format is used`() {
    git("refs migrate --ref-format=reftable")
    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(previousValue = null, projectPath)
    }
    assertEquals(masterBranch(), actual)
  }

  fun `test detached HEAD with reftable format`() {
    makeCommit("1")
    git("refs migrate --ref-format=reftable")
    git("checkout HEAD^")
    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(previousValue = null, projectPath)
    }
    assertEquals(notOnBranch(), actual)
  }

  fun `test branch is resolved with reftable sha256`() {
    val sha256Repo = initSha256ReftableRepo()
    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(previousValue = null, sha256Repo.toString())
    }
    val expected = sha256Repo.resolve(".git/HEAD").toString()
    assertEquals(
      GitRecentProjectCachedBranch.KnownBranch(branchName = "master", headFilePath = expected),
      actual,
    )
  }

  fun `test detached HEAD with reftable sha256`() {
    val sha256Repo = initSha256ReftableRepo()
    cd(sha256Repo)
    touch("second.txt")
    git(project, "add second.txt")
    git(project, "commit -m second")
    git(project, "checkout HEAD^")
    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(previousValue = null, sha256Repo.toString())
    }
    val expected = sha256Repo.resolve(".git/HEAD").toString()
    assertEquals(
      GitRecentProjectCachedBranch.NotOnBranch(expected),
      actual,
    )
  }

  fun `test reftable with long branch name exceeding filesystem NAME_MAX`() {
    // Branch names > 255 chars are impossible with loose refs (filesystem NAME_MAX limit)
    // but work fine in reftable format where refs are stored in binary files
    val longName = "feature/" + "a".repeat(300)
    git("refs migrate --ref-format=reftable")
    git("checkout -b $longName")
    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(previousValue = null, projectPath)
    }
    assertEquals(
      GitRecentProjectCachedBranch.KnownBranch(branchName = longName, headFilePath = repo.repositoryFiles.headFile.path),
      actual,
    )
  }

  fun `test reftable with near-limit branch name`() {
    // The reftable default block size is 4096 bytes. The full ref name (refs/heads/<branch>)
    // plus record overhead must fit in one block. A 3900-char branch name is close to this limit.
    val nearLimitName = "b".repeat(3900)
    git("refs migrate --ref-format=reftable")
    git("checkout -b $nearLimitName")
    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(previousValue = null, projectPath)
    }
    assertEquals(
      GitRecentProjectCachedBranch.KnownBranch(branchName = nearLimitName, headFilePath = repo.repositoryFiles.headFile.path),
      actual,
    )
  }

  fun `test reftable with branch name containing slashes`() {
    val slashyName = "team/user/feature/JIRA-1234/implement-something"
    git("refs migrate --ref-format=reftable")
    git("checkout -b $slashyName")
    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(previousValue = null, projectPath)
    }
    assertEquals(
      GitRecentProjectCachedBranch.KnownBranch(branchName = slashyName, headFilePath = repo.repositoryFiles.headFile.path),
      actual,
    )
  }

  fun `test reftable with unicode branch name`() {
    val unicodeName = "feature/добавить-функцию"
    git("refs migrate --ref-format=reftable")
    git("checkout -b $unicodeName")
    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(previousValue = null, projectPath)
    }
    assertEquals(
      GitRecentProjectCachedBranch.KnownBranch(branchName = unicodeName, headFilePath = repo.repositoryFiles.headFile.path),
      actual,
    )
  }

  fun `test project path is in git worktree with reftable format`() {
    git("refs migrate --ref-format=reftable")
    val worktree = "feature"
    git("worktree add $worktree")

    val worktreePath = projectNioRoot.resolve(worktree)

    val actual = runBlocking {
      GitRecentProjectsBranchesService.loadBranch(previousValue = null, worktreePath.toString())
    }
    val expected = repo.repositoryFiles.worktreesDirFile.resolve(worktree).resolve("HEAD").toString()
    assertEquals(
      GitRecentProjectCachedBranch.KnownBranch(
        branchName = worktree,
        headFilePath = expected,
      ),
      actual,
    )
  }

  private fun initSha256ReftableRepo(): Path {
    val repoDir = Files.createTempDirectory(projectRoot.toNioPath(), "sha256")
    cd(repoDir)
    git(project, "init --initial-branch=master --object-format=sha256")
    setupDefaultUsername()
    touch("initial.txt")
    git(project, "add initial.txt")
    git(project, "commit -m initial")
    git(project, "refs migrate --ref-format=reftable")
    cd(projectPath) // restore working dir
    return repoDir
  }

  private fun notOnBranch() = GitRecentProjectCachedBranch.NotOnBranch(repo.repositoryFiles.headFile.path)

  private fun masterBranch(): GitRecentProjectCachedBranch.KnownBranch =
    GitRecentProjectCachedBranch.KnownBranch(
      branchName = "master",
      headFilePath = repo.repositoryFiles.headFile.path
    )
}
