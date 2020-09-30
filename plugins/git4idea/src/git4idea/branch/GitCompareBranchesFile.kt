// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.editor.VcsLogFile
import javax.swing.JComponent

internal class GitCompareBranchesFile(sessionId: String, private val compareBranchesUi: GitCompareBranchesUi) :
  VcsLogFile(compareBranchesUi.getEditorTabName()), VirtualFilePathWrapper {
  private val pathId = GitCompareBranchesFilesManager.createPath(sessionId, compareBranchesUi)
  private val fileSystemInstance = GitCompareBranchesVirtualFileSystem.getInstance()

  override fun createMainComponent(project: Project): JComponent {
    val logManager = VcsProjectLog.getInstance(project).logManager
    if (logManager == null) {
      return JBPanelWithEmptyText().withEmptyText(VcsLogBundle.message("vcs.log.is.not.available"))
    }

    val component = compareBranchesUi.create(logManager)
    logManager.scheduleInitialization()
    return component
  }

  override fun getFileSystem(): GitCompareBranchesVirtualFileSystem = fileSystemInstance
  override fun getPath(): String = try {
    fileSystem.getPath(pathId)
  }
  catch (e: Exception) {
    name
  }

  override fun getPresentablePath(): String = name
  override fun enforcePresentableName(): Boolean = true

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as GitCompareBranchesFile

    return pathId == other.pathId
  }

  override fun hashCode(): Int = pathId.hashCode()
}