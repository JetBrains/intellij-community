// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkin

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import git4idea.config.GitVcsSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitPushAfterCommitDecisionTest {
  private val projectFixture = projectFixture()
  private val disposableFixture = disposableFixture()

  @Test
  fun `test hidden dialog shows for a divergence`() {
    assertThat(shows(diverged = true)).isTrue()
  }

  @Test
  fun `test hidden dialog pushes without a divergence`() {
    assertThat(shows(diverged = false)).isFalse()
  }

  @Test
  fun `test dialog shows when the settings ask for it`() {
    assertThat(shows(previewSetting = true, diverged = true)).isTrue()
    assertThat(shows(previewSetting = true, previewProtectedOnly = true, hasProtectedBranch = true)).isTrue()
  }

  @Test
  fun `test protected only setting pushes to an unprotected branch without a divergence`() {
    assertThat(shows(previewSetting = true, previewProtectedOnly = true)).isFalse()
    assertThat(shows(previewSetting = true, previewProtectedOnly = true, diverged = true)).isTrue()
  }

  @Test
  fun `test dialog shows when a push is not possible`() {
    assertThat(shows(canPush = false)).isTrue()
  }

  @Test
  fun `test hidden dialog checks the remote before a push`() {
    hidePushDialog()

    assertThat(GitPushAfterCommitDialog.isRemoteCheckNeeded(projectFixture.get())).isTrue()
  }

  @Test
  fun `test hidden dialog pushes without a remote check when the registry turns the check off`() {
    hidePushDialog()
    Registry.get("git.push.divergence.check").setValue(false, disposableFixture.get())

    assertThat(GitPushAfterCommitDialog.isRemoteCheckNeeded(projectFixture.get())).isFalse()
  }

  @Test
  fun `test shown dialog doesnt check the remote before it opens`() {
    GitVcsSettings.getInstance(projectFixture.get()).setPreviewPushOnCommitAndPush(true)
    GitVcsSettings.getInstance(projectFixture.get()).isPreviewPushProtectedOnly = false

    assertThat(GitPushAfterCommitDialog.isRemoteCheckNeeded(projectFixture.get())).isFalse()
  }

  private fun hidePushDialog() {
    GitVcsSettings.getInstance(projectFixture.get()).setPreviewPushOnCommitAndPush(false)
  }

  private fun shows(previewSetting: Boolean = false,
                    previewProtectedOnly: Boolean = false,
                    hasProtectedBranch: Boolean = false,
                    canPush: Boolean = true,
                    diverged: Boolean = false): Boolean =
    GitPushAfterCommitDialog.shouldShow(previewSetting, previewProtectedOnly, hasProtectedBranch, canPush, diverged)
}
