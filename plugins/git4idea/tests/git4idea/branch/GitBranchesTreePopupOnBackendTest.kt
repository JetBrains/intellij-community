// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import git4idea.ui.branch.popup.GitBranchesTreePopupOnBackend
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class GitBranchesTreePopupOnBackendTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `test popup can be created before GitRepositoriesHolder is initialized`(): Unit = with(context) {
    assertThat(GitRepositoriesHolder.getInstance(project).initialized).isFalse()

    invokeAndWaitIfNeeded {
      val popup: JBPopup = GitBranchesTreePopupOnBackend.create(project, repo)
      Disposer.dispose(popup)
    }
  }
}
