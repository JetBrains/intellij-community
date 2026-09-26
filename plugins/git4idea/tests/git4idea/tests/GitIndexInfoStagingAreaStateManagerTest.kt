// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.vcs.VcsException
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcsUtil.VcsUtil
import git4idea.checkin.GitIndexInfoStagingAreaStateManager
import git4idea.test.GitScenarios.unmergedFiles
import git4idea.test.GitSingleRepoContext
import git4idea.test.createSubRepository
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.tac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@TestApplication
internal class GitIndexInfoStagingAreaStateManagerTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  private lateinit var saver: GitIndexInfoStagingAreaStateManager

  @BeforeEach
  fun setUp() {
    saver = GitIndexInfoStagingAreaStateManager(context.repo)
  }

  @Test
  fun `test unmerged paths are rejected`(): Unit = with(context) {
    unmergedFiles(repo)
    file("a").create().add()

    assertThrows<VcsException> {
      saver.prepareStagingArea(emptySet(), emptySet())
    }
  }

  @Test
  fun `test staged modifications reset and restored`(): Unit = with(context) {
    file("a").create("index content a").add()
    file("b").create("index content b").add()

    overwrite("a", "work tree content a")
    overwrite("b", "work tree content b")

    verifyStagedChangesAreSavedAndLoadedCorrectly()
  }

  @Test
  fun `test staged deletions reset and restored`(): Unit = with(context) {
    tac("a")
    tac("b")
    git("rm --cached -- a b")

    verifyStagedChangesAreSavedAndLoadedCorrectly()
  }

  @Test
  fun `test staged renames reset and restored`(): Unit = with(context) {
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

  @Test
  fun `test executable file mode is preserved`(): Unit = with(context) {
    file("exec").create().add()
    git("update-index --chmod=+x exec")

    verifyStagedChangesAreSavedAndLoadedCorrectly()
  }

  @Test
  fun `test submodule entry is preserved`(): Unit = with(context) {
    val submodule = repo.createSubRepository("submodule", addToGitIgnore = false)
    git("add submodule ${submodule.root.path}")

    verifyStagedChangesAreSavedAndLoadedCorrectly()
  }

  @Test
  fun `test filtering excludes only non committed paths`(): Unit = with(context) {
    val fileA = file("a").create().add()
    val fileB = file("b").create().add()
    file("c").create().add()

    val toCommitAdded = setOf(VcsUtil.getFilePath(fileA.file.path, false))
    val toCommitRemoved = setOf(VcsUtil.getFilePath(fileB.file.path, false))

    val remainingEntries = getPorcelainStatusLines().filter { it.endsWith("a") || it.endsWith("b") }.toSet()

    val statusBefore = getPorcelainStatusLines()
    saver.prepareStagingArea(toCommitAdded, toCommitRemoved)

    assertThat(getPorcelainStatusLines()).isEqualTo(remainingEntries)

    saver.restore()
    assertThat(getPorcelainStatusLines()).isEqualTo(statusBefore)
  }

  private fun GitSingleRepoContext.verifyStagedChangesAreSavedAndLoadedCorrectly() {
    val statusBefore = getPorcelainStatusLines()
    saver.prepareStagingArea(emptySet(), emptySet())
    assertThat(getPorcelainStatusLines()).isEmpty()
    saver.restore()
    assertThat(getPorcelainStatusLines()).isEqualTo(statusBefore)
  }

  private fun GitSingleRepoContext.getPorcelainStatusLines(): Set<String> =
    git("status --porcelain=v2 --untracked-files=no").lines().filter { it.isNotBlank() }.toSet()
}
