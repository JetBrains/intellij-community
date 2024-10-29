// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.scope

import git4idea.index.vfs.filePath
import git4idea.search.GitTrackedSearchScope
import git4idea.test.GitSingleRepoTest
import git4idea.test.add
import git4idea.test.createFileStructure
import git4idea.util.GitFileUtils

class GitTrackedSearchScopeTest : GitSingleRepoTest() {
  fun `test untracked files are not in scope`() {
    val tracked = listOf("tr-1", "tr-2")
    val untracked = listOf("unt-1", "UNTRACKED")
    createFileStructure(tracked, untracked)

    getGitUntrackedSearchScope().assertScope(shouldContain = tracked, shouldNotContain = untracked)
  }

  fun `test untracked scope is updated`() {
    val relativePaths = listOf("file", "file2")
    val files = relativePaths.map {
      repo.root.createFile(it)
    }

    getGitUntrackedSearchScope().assertScope(shouldNotContain = relativePaths)
    GitFileUtils.addPaths(project, repo.root, files.map { it.filePath() }, true)
    getGitUntrackedSearchScope().assertScope(shouldContain = relativePaths)
    GitFileUtils.deleteFilesFromCache(project, repo.root, files)
    getGitUntrackedSearchScope().assertScope(shouldNotContain = relativePaths)
  }

  fun `test file outside of git repo`() {
    val file = repo.root.parent.createFile("next-to-repo")
    val scope = getGitUntrackedSearchScope()
    assertFalse(scope.isTracked(file))
  }

  private fun getGitUntrackedSearchScope(): GitTrackedSearchScope {
    awaitEvents()
    return checkNotNull(GitTrackedSearchScope.getSearchScope(project))
  }

  private fun GitTrackedSearchScope.assertScope(shouldContain: List<String> = emptyList(), shouldNotContain: List<String> = emptyList()) {
    for (path in shouldContain) {
      assertTrue("'$path' should be included in the scope", isTracked(repo.root.findFileByRelativePath(path)!!))
    }
    for (path in shouldNotContain) {
      assertFalse("'$path' should be excluded from the scope", isTracked(repo.root.findFileByRelativePath(path)!!))
    }
  }

  fun createFileStructure(tracked: List<String>, untracked: List<String>) {
    createFileStructure(repo.root, *tracked.toTypedArray(), *untracked.toTypedArray())
    tracked.forEach { add(it) }
  }
}