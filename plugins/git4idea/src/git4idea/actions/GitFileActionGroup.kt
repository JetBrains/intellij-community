// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsDataKeys
import git4idea.i18n.GitBundle
import kotlin.streams.toList

class GitFileActionGroup : DefaultActionGroup(), DumbAware {

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.text = message("group.mainmenu.vcs.current.file.text")
    val selection = e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM)?.limit(2)?.toList() ?: return
    if (e.getData(CommonDataKeys.EDITOR) == null && selection.isNotEmpty()) {
      presentation.text = if (selection[0].isDirectory) GitBundle.message("action.selected.directory.text", selection.size) else
        GitBundle.message("action.selected.file.text", selection.size)
    }
  }
}