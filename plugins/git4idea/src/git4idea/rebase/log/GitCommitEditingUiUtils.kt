// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.VcsLogNavigationUtil.jumpToCommit
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.repo.GitRepository

internal fun VcsLogUiEx.focusCommitAfterLogUpdate(repository: GitRepository, commitToFocus: Hash?) {
  commitToFocus ?: return
  VcsLogUtil.invokeOnChange(this) {
    jumpToCommit(commitToFocus, repository.root, silently = true, focus = true)
  }
}