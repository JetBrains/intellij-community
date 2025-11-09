// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ignore.actions.IgnoreFileActionGroup
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.treeStructure.Tree
import com.intellij.vcs.commit.CommitSessionCollector
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vcsUtil.VcsUtil.getVcsFor
import java.awt.event.MouseEvent
import javax.swing.tree.TreePath

internal class BackendChangesTreeCompatibilityProvider : ChangesTreeCompatibilityProvider {

  override fun getFileStatus(project: Project, file: VirtualFile): FileStatus =
    ChangeListManager.getInstance(project).getStatus(file)

  override fun logInclusionToggle(project: Project, exclude: Boolean, event: MouseEvent) {
    CommitSessionCollector.getInstance(project).logInclusionToggle(exclude, event)
  }

  override fun logInclusionToggle(project: Project, exclude: Boolean, event: AnActionEvent) {
    CommitSessionCollector.getInstance(project).logInclusionToggle(exclude, event)
  }

  override fun logFileSelected(project: Project, event: MouseEvent) {
    CommitSessionCollector.getInstance(project).logFileSelected(event)
  }

  override fun getSwitchedBranch(project: Project, file: VirtualFile): @NlsSafe String? =
    ChangeListManager.getInstance(project).getSwitchedBranch(file)

  override fun showIgnoredViewDialog(project: Project) {
    if (!project.isDisposed) IgnoredViewDialog(project).show()
  }

  override fun showUnversionedViewDialog(project: Project) {
    if (!project.isDisposed) UnversionedViewDialog(project).show()
  }

  override fun resolveLocalFile(path: String): VirtualFile? = LocalFileSystem.getInstance().findFileByPath(path)

  override fun toHijackedChange(project: Project, file: VirtualFile): Change? {
    val before = VcsCurrentRevisionProxy.create(file, project) ?: return null
    val after = CurrentContentRevision(VcsUtil.getFilePath(file))
    return Change(before, after, FileStatus.HIJACKED)
  }

  override fun getScopeVirtualFileFor(filePath: FilePath): VirtualFile? {
    if (filePath.isNonLocal()) return null
    return VcsImplUtil.findValidParentAccurately(filePath)
  }

  override fun acceptIgnoredFilesDrop(project: Project, dragOwner: ChangeListOwner, dragBean: ChangeListDragBean) {
    val tree = dragBean.sourceComponent as? Tree ?: return
    val vcs = dragBean.unversionedFiles.firstNotNullOfOrNull { file -> getVcsFor(project, file) } ?: return

    val ignoreFileType = VcsIgnoreManagerImpl.getInstanceImpl(project).findIgnoreFileType(vcs) ?: return
    val ignoreGroup = IgnoreFileActionGroup(ignoreFileType)

    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      null, ignoreGroup, DataManager.getInstance().getDataContext(dragBean.sourceComponent),
      JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
    tree.getPathBounds(TreePath(dragBean.targetNode.path))?.let { dropBounds ->
      popup.show(RelativePoint(dragBean.sourceComponent, dropBounds.location))
    }
  }
}
