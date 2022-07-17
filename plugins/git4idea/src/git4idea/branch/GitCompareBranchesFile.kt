// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.ui.editor.VcsLogFile
import com.intellij.vcs.log.util.VcsLogUtil
import java.awt.BorderLayout
import javax.swing.JComponent

internal class GitCompareBranchesFile(project: Project,
                                      name: String,
                                      path: GitCompareBranchesVirtualFileSystem.ComplexPath,
                                      private val compareBranchesUiFactory: () -> GitCompareBranchesUi) :
  VcsLogFile(name), VirtualFilePathWrapper {
  private val pathId = GitCompareBranchesFilesManager.createPath(project, path.sessionId, path.ranges, path.roots)
  private val fileSystemInstance = GitCompareBranchesVirtualFileSystem.getInstance()

  override fun createMainComponent(project: Project): JComponent {
    val panel = JBPanelWithEmptyText(BorderLayout()).withEmptyText(VcsLogBundle.message("vcs.log.is.loading"))
    VcsLogUtil.runWhenVcsAndLogIsReady(project) { logManger ->
      val component = compareBranchesUiFactory().create(logManger)
      panel.add(component, BorderLayout.CENTER)
    }
    return panel
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
