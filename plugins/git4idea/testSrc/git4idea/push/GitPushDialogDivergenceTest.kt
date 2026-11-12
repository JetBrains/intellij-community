// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.dvcs.push.ui.VcsPushDialog
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import git4idea.config.GitVcsApplicationSettings
import git4idea.test.cd
import git4idea.test.git
import git4idea.test.makeCommit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.io.path.moveTo

/**
 * Checks the push dialog with real repositories: the remote check after the commit list loads, the fetch, and the default action.
 */
@TestApplication
internal class GitPushDialogDivergenceTest {
  private val fixture = gitPushSingleRepoFixture()
  private val context: GitPushSingleRepoContext get() = fixture.get()
  private val disposableFixture = disposableFixture()

  @Test
  fun `test dialog offers fetch for an unfetched commit, then the plain push`(): Unit = with(context) {
    git("checkout -b feature")
    makeCommit("feature.txt")
    git("push -u origin feature")
    git("commit --amend -m amended")
    cd(broRepo)
    git("fetch")
    git("checkout -b feature origin/feature")
    makeCommit("bro.txt")
    git("push origin feature")
    cd(repository)

    val dialog = openPushDialog()
    waitFor("the remote check") { dialog.isRemoteCheckFinished }

    assertThat(read { dialog.isFetchRequired }).isTrue()
    assertThat(read { dialog.defaultActionText }).isEqualTo("Fetch")

    invokeAndWaitIfNeeded { dialog.fetchRemoteCommits() }
    waitFor("the reload after the fetch") { dialog.isRemoteCheckFinished && !dialog.isFetchRequired }

    assertThat(read { dialog.getRemoteCommitCount(repository) }).isEqualTo(2)
    assertThat(read { dialog.defaultActionText }).isEqualTo("Push")
  }

  @Test
  fun `test dialog keeps the plain push after an amend`(): Unit = with(context) {
    git("checkout -b feature")
    makeCommit("feature.txt")
    git("push -u origin feature")
    git("commit --amend -m amended")

    val dialog = openPushDialog()
    waitFor("the remote check") { dialog.isRemoteCheckFinished }

    assertThat(read { dialog.getRemoteCommitCount(repository) }).isEqualTo(1)
    assertThat(read { dialog.defaultActionText }).isEqualTo("Push")
  }

  @Test
  fun `test dialog keeps the plain push for a fast-forward`(): Unit = with(context) {
    git("checkout -b feature")
    makeCommit("feature.txt")
    git("push -u origin feature")
    makeCommit("more.txt")

    val dialog = openPushDialog()
    waitFor("the remote check") { dialog.isRemoteCheckFinished }

    assertThat(read { dialog.isFetchRequired }).isFalse()
    assertThat(read { dialog.getRemoteCommitCount(repository) }).isZero()
    assertThat(read { dialog.defaultActionText }).isEqualTo("Push")
  }

  @Test
  fun `test failed fetch stays in the commit list after the list is rebuilt`(): Unit = with(context) {
    cd(broRepo)
    makeCommit("bro.txt")
    git("push")
    cd(repository)
    makeCommit("mine.txt")
    val dialog = openPushDialog()
    waitFor("the remote check") { dialog.isRemoteCheckFinished }
    assertThat(read { dialog.isFetchRequired }).isTrue()
    // the remote is gone, so the fetch fails
    parentRepo.moveTo(parentRepo.resolveSibling("parent-moved"))

    invokeAndWaitIfNeeded { dialog.fetchRemoteCommits() }
    waitFor("the failed fetch") { dialog.controller.hasFetchErrorRow(repository) }
    invokeAndWaitIfNeeded { dialog.controller.showMoreCommits(repository) }

    assertThat(read { dialog.controller.hasFetchErrorRow(repository) }).isTrue()
    assertThat(read { dialog.isFetchRequired }).isFalse()
  }

  private fun GitPushSingleRepoContext.openPushDialog(): VcsPushDialog {
    val settings = GitVcsApplicationSettings.getInstance()
    val useCredentialHelper = settings.isUseCredentialHelper
    settings.isUseCredentialHelper = false
    Disposer.register(disposableFixture.get()) { settings.isUseCredentialHelper = useCredentialHelper }
    updateRepositories()

    val dialog = invokeAndWaitIfNeeded { VcsPushDialog(project, listOf(repository), repository) }
    Disposer.register(disposableFixture.get()) { invokeAndWaitIfNeeded { dialog.close(DialogWrapper.CANCEL_EXIT_CODE) } }
    return dialog
  }

  private fun <T> read(supplier: () -> T): T = invokeAndWaitIfNeeded { supplier() }

  private fun waitFor(description: String, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 60_000
    while (!read(condition)) {
      check(System.currentTimeMillis() < deadline) { "Timed out waiting for $description" }
      Thread.sleep(50)
    }
  }
}
