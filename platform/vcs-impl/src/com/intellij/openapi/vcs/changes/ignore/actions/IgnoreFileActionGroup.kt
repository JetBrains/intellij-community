// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.util.containers.isEmpty
import com.intellij.vcsUtil.VcsUtil

open class IgnoreFileActionGroup(private val ignoreFileType: IgnoreFileType) :
  ActionGroup(
    message("vcs.add.to.ignore.file.action.group.text", ignoreFileType.ignoreLanguage.filename),
    message("vcs.add.to.ignore.file.action.group.description", ignoreFileType.ignoreLanguage.filename),
    ignoreFileType.icon
  ), DumbAware {

  var actions: Collection<AnAction> = emptyList()

  override fun update(e: AnActionEvent) {
    val selectedFiles = e.getData<Array<VirtualFile>>(CommonDataKeys.VIRTUAL_FILE_ARRAY)
    val project = e.getData<Project>(CommonDataKeys.PROJECT)
    val presentation = e.presentation

    if (project == null || selectedFiles == null || ScheduleForAdditionAction.getUnversionedFiles(e, project).isEmpty()) {
      presentation.isVisible = false
      return
    }

    val ignoreFiles = filterSelectedFiles(project, selectedFiles).map { findSuitableIgnoreFiles(project, it) }
    val resultedIgnoreFiles = ignoreFiles.flatten().toHashSet()

    for (files in ignoreFiles) {
      resultedIgnoreFiles.retainAll(files) //only take ignore files which is suitable for all selected files
    }

    actions = resultedIgnoreFiles.toActions(project)

    isPopup = actions.size > 1
    presentation.isVisible = actions.isNotEmpty()
  }

  override fun getChildren(e: AnActionEvent?) = actions.toTypedArray()

  private fun filterSelectedFiles(project: Project, files: Array<VirtualFile>) =
    files.filter { file ->
      VcsUtil.isFileUnderVcs(project, VcsUtil.getFilePath(file)) && !ChangeListManager.getInstance(project).isIgnoredFile(file)
    }

  private fun findSuitableIgnoreFiles(project: Project, file: VirtualFile): Collection<VirtualFile> {
    val fileParent = file.parent
    return FileTypeIndex.getFiles(ignoreFileType, ProjectScope.getProjectScope(project))
      .filter {
        fileParent == it.parent || fileParent != null && it.parent != null && VfsUtil.isAncestor(it.parent, fileParent, false)
      }
  }

  private fun Collection<VirtualFile>.toActions(project: Project) =
    map { file ->
      IgnoreFileAction(file).apply {
        templatePresentation.apply {
          icon = ignoreFileType.icon
          text = file.toTextRepresentation(project, this@toActions.size)
        }
      }
    }

  private fun VirtualFile.toTextRepresentation(project: Project, size: Int): String {
    if (size == 1) return message("vcs.add.to.ignore.file.action.group.text", ignoreFileType.ignoreLanguage.filename)
    val vcsRoot = VcsUtil.getVcsRootFor(project, this) ?: return name

    return VfsUtil.getRelativePath(this, vcsRoot) ?: name
  }
}
