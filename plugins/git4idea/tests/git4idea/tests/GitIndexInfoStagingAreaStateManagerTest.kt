// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.vcs.Executor.overwrite
import com.intellij.openapi.vcs.VcsException
import com.intellij.vcsUtil.VcsUtil
import git4idea.checkin.GitIndexInfoStagingAreaStateManager
import git4idea.test.GitScenarios.unmergedFiles
import git4idea.test.GitSingleRepoTest
import git4idea.test.createSubRepository
import git4idea.test.tac
import org.junit.jupiter.api.assertThrows

internal class GitIndexInfoStagingAreaStateManagerTest : GitSingleRepoTest() {
  private lateinit var saver: GitIndexInfoStagingAreaStateManager

  override fun setUp() {
    super.setUp()
    saver = GitIndexInfoStagingAreaStateManager(repo)
  }

  fun `test unmerged paths are rejected`() {
    unmergedFiles(repo)
    file("a").create().add()

    assertThrows<VcsException> {
      saver.prepareStagingArea(emptySet(), emptySet())
    }
  }

  fun `test staged modifications reset and restored`() {
    file("a").create("index content a").add()
    file("b").create("index content b").add()

    overwrite("a", "work tree content a")
    overwrite("b", "work tree content b")

    verifyStagedChangesAreSavedAndLoadedCorrectly()
  }

  fun `test staged deletions reset and restored`() {
    tac("a")
    tac("b")
    git("rm --cached -- a b")

    verifyStagedChangesAreSavedAndLoadedCorrectly()
  }

  fun `test staged renames reset and restored`() {
    val initialName = "a"
    val renamed = "b"
    tac(initialName)
    val content = file(initialName).read()
    git("mv $initialName $renamed")

    // restore working tree, as --cached option is not supported
    file(renamed).delete()
    file(initialName).create(content)

    verifyStagedChangesAreSavedAndLoadedCorrectly()
  }

  fun `test executable file mode is preserved`() {
    file("exec").create().add()
    git("update-index --chmod=+x exec")

    verifyStagedChangesAreSavedAndLoadedCorrectly()
  }

  fun `test submodule entry is preserved`() {
    val submodule = repo.createSubRepository("submodule", addToGitIgnore = false)
    git("add submodule ${submodule.root.path}")

    verifyStagedChangesAreSavedAndLoadedCorrectly()
  }

  fun `test filtering excludes only non committed paths`() {
    val fileA = file("a").create().add()
    val fileB = file("b").create().add()
    file("c").create().add()

    val toCommitAdded = setOf(VcsUtil.getFilePath(fileA.file.path, false))
    val toCommitRemoved = setOf(VcsUtil.getFilePath(fileB.file.path, false))

    val remainingEntries = getPorcelainStatusLines().filter { it.endsWith("a") || it.endsWith("b") }.toSet()

    val statusBefore = getPorcelainStatusLines()
    saver.prepareStagingArea(toCommitAdded, toCommitRemoved)

    assertEquals(remainingEntries, getPorcelainStatusLines())

    saver.restore()
    assertEquals(statusBefore, getPorcelainStatusLines())
  }

  private fun verifyStagedChangesAreSavedAndLoadedCorrectly() {
    val statusBefore = getPorcelainStatusLines()
    saver.prepareStagingArea(emptySet(), emptySet())
    assertEquals(emptySet<String>(), getPorcelainStatusLines())
    saver.restore()
    assertEquals(statusBefore, getPorcelainStatusLines())
  }

  private fun getPorcelainStatusLines(): Set<String> = git("status --porcelain=v2 --untracked-files=no").lines().filter { it.isNotBlank() }.toSet()
}