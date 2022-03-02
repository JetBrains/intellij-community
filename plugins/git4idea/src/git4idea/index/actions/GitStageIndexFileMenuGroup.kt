// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.ide.actions.NonEmptyActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.UpdateInBackground
import git4idea.index.vfs.GitIndexVirtualFile

class GitStageIndexFileMenuGroup : NonEmptyActionGroup(), UpdateInBackground {
  override fun update(event: AnActionEvent) {
    event.presentation.isVisible = childrenCount > 0 &&
                                   event.getData(CommonDataKeys.VIRTUAL_FILE) is GitIndexVirtualFile
  }
}