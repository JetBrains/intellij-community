// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.scope

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.testFramework.junit5.TestApplication
import git4idea.index.vfs.filePath
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.search.GitIgnoreSearchScope
import git4idea.test.GitSingleRepoContext
import git4idea.test.createFile
import git4idea.test.createFileStructure
import git4idea.test.createSubRepository
import git4idea.test.gitSingleRepoContextFixture
import git4idea.util.GitFileUtils
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class GitIgnoreSearchScopeTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `test no gitignore`(): Unit = with(context) {
    val fileName = "file"
    createFile(repo.root, fileName)
    assertScope(getGitIgnoreSearchScope(), shouldContain = listOf(fileName))
  }

  @Test
  fun `test ignored files are not in scope`(): Unit = with(context) {
    val ignoredFiles = listOf("1/ignore", "1/file", "ignore")
    val includedFiles = listOf("file", "2/file", "another-file")

    createFileStructure(repo.root, *ignoredFiles.toTypedArray(), *includedFiles.toTypedArray())

    gitIgnore("ignore")
    gitIgnore(repo.root.findOrCreateDirectory("1"), "file")

    assertScope(getGitIgnoreSearchScope(), shouldContain = includedFiles, shouldNotContain = ignoredFiles)
  }

  @Test
  fun `test explicitly added ignored files are in scope`(): Unit = with(context) {
    val toBeIgnored = "to-be-ignored"
    gitIgnore("**")
    val toBeIgnoredFile = createFile(repo.root, toBeIgnored)
    assertScope(getGitIgnoreSearchScope(), shouldNotContain = listOf(toBeIgnored))

    GitFileUtils.addPaths(project, repo.root, listOf(toBeIgnoredFile.filePath()), true)

    assertScope(getGitIgnoreSearchScope(), shouldContain = listOf(toBeIgnored))
  }

  @Test
  fun `test gitignore added and deleted`(): Unit = with(context) {
    val txtFiles = listOf("1.txt", "2.txt")
    val notTxtFiles = listOf("1.png", "2.png")

    createFileStructure(repo.root, *txtFiles.toTypedArray(), *notTxtFiles.toTypedArray())
    val gitIgnore = gitIgnore("*.txt")
    assertScope(getGitIgnoreSearchScope(), shouldContain = notTxtFiles, shouldNotContain = txtFiles)

    runBlocking {
      edtWriteAction {
        gitIgnore.delete(this)
      }
    }
    assertScope(getGitIgnoreSearchScope(), shouldContain = notTxtFiles + txtFiles)

    gitIgnore("**")
    assertScope(getGitIgnoreSearchScope(), shouldNotContain = notTxtFiles + txtFiles)
  }

  @Test
  fun `test excluded files are not in scope`(): Unit = with(context) {
    val module = runBlocking {
      edtWriteAction {
        ModuleManager.getInstance(project).newModule(projectRoot.path + "/scope-test.iml", "JAVA_MODULE")
      }
    }
    ModuleRootModificationUtil.addContentRoot(module, projectRoot)
    ModuleRootModificationUtil.updateExcludedFolders(module, projectRoot, emptyList(), listOf(projectRoot.url + "/excluded"))

    val filesNotInScope = listOf("1.txt", "excluded/file")
    createFileStructure(repo.root, *filesNotInScope.toTypedArray())
    gitIgnore("*.txt")
    val scope = getGitIgnoreSearchScope()

    assertThat(scope.isSearchInLibraries).isFalse()
    assertThat(scope.isSearchInModuleContent(module)).isTrue()

    for (path in filesNotInScope) {
      runBlocking {
        readAction {
          assertThat(scope.contains(repo.root.findFileByRelativePath(path)!!))
            .describedAs("'%s' should be excluded from the scope", path)
            .isFalse()
        }
      }
    }
  }

  @Test
  fun `test nested repo gitignore scope`(): Unit = with(context) {
    // Sub repository name is added to repo .gitignore
    val nestedRepo = repo.createSubRepository("nested")

    val nestedGitIgnore = gitIgnore(nestedRepo.root, "*.txt")

    val ignoredFiles = listOf("nested/1.txt")
    val includedFiles = listOf("1.txt", "nested/file")

    createFileStructure(repo.root, *ignoredFiles.toTypedArray(), *includedFiles.toTypedArray())

    assertScope(getGitIgnoreSearchScope(), shouldContain = includedFiles, shouldNotContain = ignoredFiles)

    runBlocking {
      edtWriteAction {
        nestedGitIgnore.delete(this)
      }
    }

    assertScope(getGitIgnoreSearchScope(), shouldContain = includedFiles + ignoredFiles)
  }

  @Test
  fun `test nested repo gitignore scope with deeper hierrarchy`(): Unit = with(context) {
    // Sub repository name is added to repo .gitignore
    val nestedRepo = repo.createSubRepository("deps/subprojects/nested")

    gitIgnore(repo.root, "deps/**")
    gitIgnore(nestedRepo.root, "*.txt")

    val ignoredFiles = listOf("deps/subprojects/nested/1.txt")
    val includedFiles = listOf("1.txt", "deps/subprojects/nested/file")

    createFileStructure(repo.root, *ignoredFiles.toTypedArray(), *includedFiles.toTypedArray())

    assertScope(getGitIgnoreSearchScope(), shouldContain = includedFiles, shouldNotContain = ignoredFiles)
  }

  private fun GitSingleRepoContext.getGitIgnoreSearchScope(): GitIgnoreSearchScope {
    awaitEvents()
    return checkNotNull(GitIgnoreSearchScope.getSearchScope(project))
  }

  private fun GitSingleRepoContext.assertScope(
    scope: GitIgnoreSearchScope,
    shouldContain: List<String> = emptyList(),
    shouldNotContain: List<String> = emptyList(),
  ) {
    for (path in shouldContain) {
      assertThat(scope.isIgnored(repo.root.findFileByRelativePath(path)!!)).describedAs("'%s' should be included in the scope", path).isFalse()
    }
    for (path in shouldNotContain) {
      assertThat(scope.isIgnored(repo.root.findFileByRelativePath(path)!!)).describedAs("'%s' should be excluded from the scope", path).isTrue()
    }
  }

  private fun GitSingleRepoContext.gitIgnore(content: String) = gitIgnore(repo.root, content)
  private fun gitIgnore(parentDit: VirtualFile, content: String) = context.createFile(parentDit, GITIGNORE, content)
}