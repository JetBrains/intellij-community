// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
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
    val sourceFile = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE)
    val root = getRoot(project, sourceFile) ?: return
    val targetFile = if (showStaged) {
      GitIndexFileSystemRefresher.getInstance(project).getFile(root, sourceFile.filePath())
    }
    else {
      sourceFile.filePath().virtualFile
    } ?: return

    val caret = e.getData(CommonDataKeys.CARET)
    if (caret == null) {
      OpenSourceUtil.navigate(OpenFileDescriptor(project, targetFile))
      return
    }

    val targetPosition = getTargetPosition(project, sourceFile, targetFile, caret)
    OpenSourceUtil.navigate(OpenFileDescriptor(project, targetFile, targetPosition.line, targetPosition.column))
  }

  private fun getTargetPosition(project: Project, sourceFile: VirtualFile, targetFile: VirtualFile, caret: Caret): LogicalPosition {
    val lst = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(if (showStaged) sourceFile else targetFile)
              ?: return caret.logicalPosition
    if (lst !is GitStageLineStatusTracker) return caret.logicalPosition
    val line = if (showStaged) {
      lst.transferLineFromLocalToStaged(caret.logicalPosition.line, true)
    }
    else {
      lst.transferLineFromStagedToLocal(caret.logicalPosition.line, true)
    }
    return LogicalPosition(line, caret.logicalPosition.column)
  }
}

class GitShowStagedVersionAction : GitStageShowVersionAction(true)
class GitShowLocalVersionAction : GitStageShowVersionAction(false)