// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.scope

import com.intellij.testFramework.junit5.TestApplication
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.search.GitTrackedSearchScope
import git4idea.test.GitSingleRepoContext
import git4idea.test.add
import git4idea.test.createFile
import git4idea.test.createFileStructure
import git4idea.test.gitSingleRepoContextFixture
import git4idea.util.GitFileUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class GitTrackedSearchScopeTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `test untracked files are not in scope`(): Unit = with(context) {
    val tracked = listOf("tr-1", "tr-2")
    val untracked = listOf("unt-1", "UNTRACKED")
    createFileStructure(tracked, untracked)

    assertScope(getGitUntrackedSearchScope(), shouldContain = tracked, shouldNotContain = untracked)
  }

  @Test
  fun `test untracked scope is updated`(): Unit = with(context) {
    val relativePaths = listOf("file", "file2")
    createFileStructure(tracked = relativePaths, untracked = emptyList())

    assertScope(getGitUntrackedSearchScope(), shouldContain = relativePaths)
    GitFileUtils.deleteFilesFromCache(project, repo.root, relativePaths.map { repo.root.findFileByRelativePath(it)!! })
    assertScope(getGitUntrackedSearchScope(), shouldNotContain = relativePaths)
  }

  @Test
  fun `test file outside of git repo`(): Unit = with(context) {
    val file = createFile(repo.root.parent, "next-to-repo")
    val scope = getGitUntrackedSearchScope()
    assertThat(scope.isTracked(file)).isFalse()
  }

  @Test
  fun `test ignored files are not in scope`(): Unit = with(context) {
    val ignoredFile = createFile(repo.root, "ignored")
    createFile(repo.root, GITIGNORE, ignoredFile.name)

    assertThat(getGitUntrackedSearchScope().isTracked(ignoredFile)).isFalse()
  }

  private fun GitSingleRepoContext.getGitUntrackedSearchScope(): GitTrackedSearchScope {
    awaitEvents()
    return checkNotNull(GitTrackedSearchScope.getSearchScope(project))
  }

  private fun GitSingleRepoContext.assertScope(
    scope: GitTrackedSearchScope,
    shouldContain: List<String> = emptyList(),
    shouldNotContain: List<String> = emptyList(),
  ) {
    for (path in shouldContain) {
      assertThat(scope.isTracked(repo.root.findFileByRelativePath(path)!!)).describedAs("'%s' should be included in the scope", path).isTrue()
    }
    for (path in shouldNotContain) {
      assertThat(scope.isTracked(repo.root.findFileByRelativePath(path)!!)).describedAs("'%s' should be excluded from the scope", path).isFalse()
    }
  }

  private fun GitSingleRepoContext.createFileStructure(tracked: List<String>, untracked: List<String>) {
    createFileStructure(repo.root, *tracked.toTypedArray(), *untracked.toTypedArray())
    tracked.forEach { add(it) }
  }
}