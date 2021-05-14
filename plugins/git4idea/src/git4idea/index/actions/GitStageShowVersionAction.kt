// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.OpenSourceUtil
import git4idea.index.*
import git4idea.index.vfs.GitIndexFileSystemRefresher
import git4idea.index.vfs.GitIndexVirtualFile
import git4idea.index.vfs.filePath

abstract class GitStageShowVersionAction(private val showStaged: Boolean) : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    if (project == null || !isStagingAreaAvailable(project) || file == null
        || (file is GitIndexVirtualFile == showStaged)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val status = GitStageTracker.getInstance(project).status(file)
    e.presentation.isVisible = (status != null)
    e.presentation.isEnabled = (status?.has(if (showStaged) ContentVersion.STAGED else ContentVersion.LOCAL) == true)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val file = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE)
    val root = getRoot(project, file) ?: return
    val virtualFile = if (showStaged) {
      GitIndexFileSystemRefresher.getInstance(project).getFile(root, file.filePath())
    } else {
      file.filePath().virtualFile
    } ?: return
    OpenSourceUtil.navigate(OpenFileDescriptor(project, virtualFile))
  }
}

class GitShowStagedVersionAction : GitStageShowVersionAction(true)
class GitShowLocalVersionAction : GitStageShowVersionAction(false)