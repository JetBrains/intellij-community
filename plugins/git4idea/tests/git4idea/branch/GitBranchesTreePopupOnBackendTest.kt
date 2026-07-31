// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.test.GitSingleRepoTest
import git4idea.ui.branch.popup.GitBranchesTreePopupOnBackend

class GitBranchesTreePopupOnBackendTest : GitSingleRepoTest() {
  fun `test popup can be created before GitRepositoriesHolder is initialized`() {
    assertFalse(GitRepositoriesHolder.getInstance(project).initialized)

    invokeAndWaitIfNeeded {
      val popup: JBPopup = GitBranchesTreePopupOnBackend.create(project, repo)
      Disposer.dispose(popup)
    }
  }
}
