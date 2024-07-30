// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.scope

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vcs.changes.VcsIgnoreManagerImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.psi.search.SearchScopeProvider
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import git4idea.index.vfs.filePath
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.search.GitIgnoreSearchScope
import git4idea.search.GitSearchScopeProvider
import git4idea.test.GitSingleRepoTest
import git4idea.test.createFileStructure
import git4idea.test.createSubRepository
import git4idea.util.GitFileUtils
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class GitSearchScopeTest : GitSingleRepoTest() {
  fun `test no gitignore`() {
    val fileName = "file"
    repo.root.createFile(fileName)
    getGitIgnoreSearchScope().assertScope(shouldContain = listOf(fileName))
  }

  fun `test ignored files are not in scope`() {
    val ignoredFiles = listOf("1/ignore", "1/file", "ignore")
    val includedFiles = listOf("file", "2/file", "another-file")

    createFileStructure(repo.root, *ignoredFiles.toTypedArray(), *includedFiles.toTypedArray())

    gitIgnore("ignore")
    gitIgnore(repo.root.findOrCreateDirectory("1"), "file")

    getGitIgnoreSearchScope().assertScope(shouldContain = includedFiles, shouldNotContain = ignoredFiles)
  }

  fun `test explicitly added ignored files are in scope`() {
    val toBeIgnored = "to-be-ignored"
    gitIgnore("**")
    val toBeIgnoredFile = repo.root.createFile(toBeIgnored)
    getGitIgnoreSearchScope().assertScope(shouldNotContain = listOf(toBeIgnored))

    GitFileUtils.addPaths(project, repo.root, listOf(toBeIgnoredFile.filePath()), true)

    getGitIgnoreSearchScope().assertScope(shouldContain = listOf(toBeIgnored))
  }

  fun `test gitignore added and deleted`() {
    val txtFiles = listOf("1.txt", "2.txt")
    val notTxtFiles = listOf("1.png", "2.png")

    createFileStructure(repo.root, *txtFiles.toTypedArray(), *notTxtFiles.toTypedArray())
    val gitIgnore = gitIgnore("*.txt")
    getGitIgnoreSearchScope().assertScope(shouldContain = notTxtFiles, shouldNotContain = txtFiles)

    runBlocking {
      writeAction {
        gitIgnore.delete(this)
      }
    }
    getGitIgnoreSearchScope().assertScope(shouldContain = notTxtFiles + txtFiles)

    gitIgnore("**")
    getGitIgnoreSearchScope().assertScope(shouldNotContain = notTxtFiles + txtFiles)
  }

  fun `test excluded files are not in scope`() {
    val module = createMainModule()
    ModuleRootModificationUtil.addContentRoot(module, projectRoot)
    ModuleRootModificationUtil.updateExcludedFolders(module, projectRoot, emptyList(), listOf(projectRoot.url + "/excluded"))

    val filesNotInScope = listOf("1.txt", "excluded/file")
    createFileStructure(repo.root, *filesNotInScope.toTypedArray())
    gitIgnore("*.txt")
    val scope = getGitIgnoreSearchScope()

    assertFalse(scope.isSearchInLibraries)
    assertTrue(scope.isSearchInModuleContent(module))

    for (path in filesNotInScope) {
      runBlocking {
        readAction {
          assertFalse("'$path' should be excluded from the scope", scope.contains(repo.root.findFileByRelativePath(path)!!))
        }
      }
    }
  }

  fun `test nested repo gitignore scope`() {
    // Sub repository name is added to repo .gitignore
    val nestedRepo = repo.createSubRepository("nested")

    val nestedGitIgnore = gitIgnore(nestedRepo.root, "*.txt")

    val ignoredFiles = listOf("nested/1.txt")
    val includedFiles = listOf("1.txt", "nested/file")

    createFileStructure(repo.root, *ignoredFiles.toTypedArray(), *includedFiles.toTypedArray())

    getGitIgnoreSearchScope().assertScope(shouldContain = includedFiles, shouldNotContain = ignoredFiles)

    runBlocking {
      writeAction {
        nestedGitIgnore.delete(this)
      }
    }

    getGitIgnoreSearchScope().assertScope(shouldContain = includedFiles + ignoredFiles)
  }

  fun `test nested repo gitignore scope with deeper hierrarchy`() {
    // Sub repository name is added to repo .gitignore
    val nestedRepo = repo.createSubRepository("deps/subprojects/nested")

    gitIgnore(repo.root, "deps/**")
    gitIgnore(nestedRepo.root, "*.txt")

    val ignoredFiles = listOf("deps/subprojects/nested/1.txt")
    val includedFiles = listOf("1.txt", "deps/subprojects/nested/file")

    createFileStructure(repo.root, *ignoredFiles.toTypedArray(), *includedFiles.toTypedArray())

    getGitIgnoreSearchScope().assertScope(shouldContain = includedFiles, shouldNotContain = ignoredFiles)
  }

  fun `test no scope is provided if no git repo registered`() {
    val scopeProvider = SearchScopeProvider.EP_NAME.extensionList.filterIsInstance<GitSearchScopeProvider>().single()
    awaitEvents()
    assertNotEmpty(scopeProvider.getGeneralSearchScopes(project, DataContext.EMPTY_CONTEXT))
    vcsManager.unregisterVcs(vcs)
    VcsRepositoryManager.getInstance(myProject).waitForAsyncTaskCompletion()
    assertEmpty(scopeProvider.getGeneralSearchScopes(project, DataContext.EMPTY_CONTEXT))
  }

  private fun getGitIgnoreSearchScope(): GitIgnoreSearchScope {
    awaitEvents()
    return checkNotNull(GitIgnoreSearchScope.getSearchScope(project))
  }

  private fun awaitEvents() {
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()
    VcsIgnoreManagerImpl.getInstanceImpl(project).ignoreRefreshQueue.waitForAllExecuted(10, TimeUnit.SECONDS)
  }

  private fun GitIgnoreSearchScope.assertScope(shouldContain: List<String> = emptyList(), shouldNotContain: List<String> = emptyList()) {
    for (path in shouldContain) {
      assertFalse("'$path' should be included in the scope", isIgnored(repo.root.findFileByRelativePath(path)!!))
    }
    for (path in shouldNotContain) {
      assertTrue("'$path' should be excluded from the scope", isIgnored(repo.root.findFileByRelativePath(path)!!))
    }
  }

  private fun gitIgnore(content: String) = gitIgnore(repo.root, content)
  private fun gitIgnore(parentDit: VirtualFile, content: String) = parentDit.createFile(GITIGNORE, content)
}