// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionExtensionProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase

class GitShowStandaloneDiffFromStashAction : ShowDiffAction() {
  override fun isActive(e: AnActionEvent): Boolean {
    return  super.isActive(e) &&
            e.getData(GitStashTree.GIT_STASH_TREE_FLAG) == true &&
            e.getData(ChangesBrowserBase.DATA_KEY) == null
  }
}
