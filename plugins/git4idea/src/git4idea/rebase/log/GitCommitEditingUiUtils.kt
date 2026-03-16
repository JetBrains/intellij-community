// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.openapi.application.UI
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.showCommit
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.impl.awaitContainsCommit
import com.intellij.vcs.log.ui.VcsLogUiEx
import git4idea.repo.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun VcsLogUiEx.focusCommitWhenReady(repository: GitRepository, commitToFocus: Hash?) {
  commitToFocus ?: return

  withContext(Dispatchers.UI) {
    val logManager = VcsProjectLog.getInstance(repository.project).logManager ?: return@withContext
    if (logManager.awaitContainsCommit(commitToFocus, repository.root)) {
      showCommit(commitToFocus, repository.root, true)
    }
  }
}