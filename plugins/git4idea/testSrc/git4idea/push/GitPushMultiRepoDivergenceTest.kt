// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.dvcs.branch.DvcsSyncSettings
import com.intellij.dvcs.push.ui.VcsPushDialog
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.vcs.test.refresh
import git4idea.checkin.GitPushAfterCommitDialog
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.cd
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.makeCommit
import git4idea.test.setupRepositories
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Checks which repositories the remote check of a push covers, when the project has several repositories.
 */
@TestApplication
internal class GitPushMultiRepoDivergenceTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()
  private val disposableFixture = disposableFixture()

  private lateinit var ultimate: GitRepository
  private lateinit var community: GitRepository

  @BeforeEach
  fun beforeEach() {
    with(context) {
      val mainRepo = setupRepositories(projectPath, "parent", "bro")

      val communityDir = File(projectPath, "community")
      check(communityDir.mkdir()) { "Couldn't create $communityDir" }
      val enclosingRepo = setupRepositories(communityDir.path, "community_parent", "community_bro")

      cd(projectPath)
      refresh()
      updateRepositories()

      ultimate = mainRepo.projectRepo
      community = enclosingRepo.projectRepo
    }
  }

  @Test
  fun `test dialog checks the remote only for the selected repositories`(): Unit = with(context) {
    amendPushedCommit(ultimate)
    amendPushedCommit(community)

    val dialog = openPushDialog(ultimate)
    waitFor("the remote check of the selected repository") {
      dialog.isRemoteCheckFinished && dialog.controller.isCommitsLoaded(community)
    }

    assertThat(read { dialog.controller.isRemoteChecked(ultimate) }).isTrue()
    assertThat(read { dialog.controller.isRemoteChecked(community) }).isFalse()
  }

  @Test
  fun `test dialog checks the remote of a repository that the user selects`(): Unit = with(context) {
    amendPushedCommit(ultimate)
    amendPushedCommit(community)
    val dialog = openPushDialog(ultimate)
    waitFor("the commits of the other repository") { dialog.controller.isCommitsLoaded(community) }

    invokeAndWaitIfNeeded { dialog.controller.setRepositorySelected(community, true) }

    waitFor("the remote check of the selected repository") { dialog.controller.isRemoteChecked(community) }
    assertThat(read { dialog.getRemoteCommitCount(community) }).isEqualTo(1)
  }

  @Test
  fun `test dialog checks the remote of a repository whose target the user confirms`(): Unit = with(context) {
    amendPushedCommit(ultimate)
    amendPushedCommit(community)
    val dialog = openPushDialog(ultimate)
    waitFor("the commits of the other repository") { dialog.controller.isCommitsLoaded(community) }

    invokeAndWaitIfNeeded { dialog.controller.confirmTarget(community) }

    waitFor("the remote check of the confirmed repository") { dialog.controller.isRemoteChecked(community) }
    assertThat(read { dialog.getRemoteCommitCount(community) }).isEqualTo(1)
  }

  @Test
  fun `test commit and push without the dialog checks the committed repositories`(): Unit = with(context) {
    assertThat(GitPushAfterCommitDialog.getPushedRepositories(project, listOf(ultimate))).containsExactly(ultimate)
  }

  @Test
  fun `test commit and push without the dialog checks all repositories when the settings push all of them`(): Unit = with(context) {
    (AdvancedSettings.getInstance() as AdvancedSettingsImpl).setSetting("vcs.push.all.with.commits", true, disposableFixture.get())

    assertThat(GitPushAfterCommitDialog.getPushedRepositories(project, listOf(ultimate))).containsExactlyInAnyOrder(ultimate, community)
  }

  @Test
  fun `test commit and push without the dialog checks all repositories when the settings sync the branches`(): Unit = with(context) {
    val settings = GitVcsSettings.getInstance(project)
    val syncSetting = settings.syncSetting
    settings.syncSetting = DvcsSyncSettings.Value.SYNC
    Disposer.register(disposableFixture.get()) { settings.syncSetting = syncSetting }

    assertThat(GitPushAfterCommitDialog.getPushedRepositories(project, listOf(ultimate))).containsExactlyInAnyOrder(ultimate, community)
  }

  private fun GitPlatformTestContext.amendPushedCommit(repository: GitRepository) {
    cd(repository)
    makeCommit("file.txt")
    git("push origin master")
    git("commit --amend -m amended")
  }

  private fun GitPlatformTestContext.openPushDialog(selected: GitRepository): VcsPushDialog {
    val settings = GitVcsApplicationSettings.getInstance()
    val useCredentialHelper = settings.isUseCredentialHelper
    settings.isUseCredentialHelper = false
    Disposer.register(disposableFixture.get()) { settings.isUseCredentialHelper = useCredentialHelper }
    updateRepositories()

    val dialog = invokeAndWaitIfNeeded { VcsPushDialog(project, listOf(selected), selected) }
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
