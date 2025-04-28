// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.OpenSourceUtil
import git4idea.GitDisposable
import git4idea.i18n.GitBundle
import git4idea.index.*
import git4idea.index.vfs.GitIndexFileSystemRefresher
import git4idea.index.vfs.GitIndexVirtualFile
import git4idea.index.vfs.filePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

abstract class GitStageShowVersionAction(private val showStaged: Boolean) : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

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
    val project = e.project ?: return
    val sourceFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val root = getRoot(project, sourceFile) ?: return
    val caret = e.getData(CommonDataKeys.CARET)

    if (showStaged) {
      GitDisposable.getInstance(project).childScope("Show staged file").async {
        val filePath = sourceFile.filePath()
        withBackgroundProgress(project, GitBundle.message("stage.vfs.read.process", filePath.name), true) {
          val targetFile = GitIndexFileSystemRefresher.getInstance(project).createFile(root, filePath) ?: return@withBackgroundProgress
          withContext(Dispatchers.EDT) {
            navigateToFile(project, targetFile, sourceFile, caret)
          }
        }
      }
    }
    else {
      val targetFile = sourceFile.filePath().virtualFile ?: return
      navigateToFile(project, targetFile, sourceFile, caret)
    }
  }

  private fun navigateToFile(project: Project, targetFile: VirtualFile, sourceFile: VirtualFile, caret: Caret?) {
    if (caret == null) {
      OpenSourceUtil.navigate(OpenFileDescriptor(project, targetFile))
    }
    else {
      val targetPosition = getTargetPosition(project, sourceFile, targetFile, caret)
      OpenSourceUtil.navigate(OpenFileDescriptor(project, targetFile, targetPosition.line, targetPosition.column))
    }
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