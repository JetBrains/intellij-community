// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ignore.actions.IgnoreFileActionGroup
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.platform.vcs.changes.ChangesUtil.getFilePath
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.PlatformIcons
import com.intellij.vcs.commit.CommitSessionCollector
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vcsUtil.VcsUtil.getVcsFor
import java.awt.Color
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.tree.TreePath

internal class BackendChangesTreeCompatibilityProvider : ChangesTreeCompatibilityProvider {
  override fun getBackgroundColorFor(project: Project, obj: Any?): Color? {
    val file = when (obj) {
      is FilePath -> getScopeVirtualFileFor(obj)
      is Change -> getScopeVirtualFileFor(getFilePath(obj))
      else -> obj as? VirtualFile?
    }

    return if (file != null) VfsPresentationUtil.getFileBackgroundColor(project, file) else null
  }

  override fun getPresentablePath(project: Project?, path: VirtualFile, useRelativeRootPaths: Boolean, acceptEmptyPath: Boolean): @NlsSafe String =
    VcsUtil.getPresentablePath(project, path, true, true)

  override fun getIcon(project: Project?, filePath: FilePath, isDirectory: Boolean): Icon? {
    val icon = FilePathIconProvider.EP_NAME.computeSafeIfAny({ provider -> provider.getIcon(filePath, isDirectory, project) })
    if (icon != null) {
      return icon
    }

    if (isDirectory) {
      return PlatformIcons.FOLDER_ICON
    }

    return VcsUtil.getIcon(project, filePath)
  }

  override fun getFileStatus(project: Project, file: VirtualFile): FileStatus =
    ChangeListManager.getInstance(project).getStatus(file)

  override fun getPresentablePath(project: Project?, path: FilePath, useRelativeRootPaths: Boolean, acceptEmptyPath: Boolean): @NlsSafe String =
    VcsUtil.getPresentablePath(project, path, true, true)

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

  override fun showResolveConflictsDialog(project: Project, changes: List<Change>) {
    AbstractVcsHelper.getInstance(project).showMergeDialog(ChangesUtil.iterateFiles(changes).toList())
  }

  override fun showIgnoredViewDialog(project: Project) {
    if (!project.isDisposed) IgnoredViewDialog(project).show()
  }

  override fun isIgnoredInUpdateMode(project: Project): Boolean {
    return ChangeListManagerImpl.getInstanceImpl(project).isIgnoredInUpdateMode
  }

  override fun showUnversionedViewDialog(project: Project) {
    if (!project.isDisposed) UnversionedViewDialog(project).show()
  }

  override fun isUnversionedInUpdateMode(project: Project): Boolean {
    return ChangeListManagerImpl.getInstanceImpl(project).isUnversionedInUpdateMode
  }

  override fun resolveLocalFile(path: String): VirtualFile? = LocalFileSystem.getInstance().findFileByPath(path)

  private fun getScopeVirtualFileFor(filePath: FilePath): VirtualFile? {
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
