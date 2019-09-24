// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil
import kotlin.streams.toList

open class IgnoreFileActionGroup(private val ignoreFileType: IgnoreFileType) :
  ActionGroup(
    message("vcs.add.to.ignore.file.action.group.text", ignoreFileType.ignoreLanguage.filename),
    message("vcs.add.to.ignore.file.action.group.description", ignoreFileType.ignoreLanguage.filename),
    ignoreFileType.icon
  ), DumbAware {

  var actions: Collection<AnAction> = emptyList()

  override fun update(e: AnActionEvent) {
    val selectedFiles = getSelectedFiles(e)
    val presentation = e.presentation

    val project = e.getData(CommonDataKeys.PROJECT)
    if (project == null) {
      presentation.isVisible = false
      return
    }

    val unversionedFiles = ScheduleForAdditionAction.getUnversionedFiles(e, project).toList()
    if (unversionedFiles.isEmpty()) {
      presentation.isVisible = false
      return
    }

    val ignoreFiles =
      filterSelectedFiles(project, selectedFiles).map { findSuitableIgnoreFiles(project, it) }.filterNot(Collection<*>::isEmpty)
    val resultedIgnoreFiles = ignoreFiles.flatten().toHashSet()

    for (files in ignoreFiles) {
      resultedIgnoreFiles.retainAll(files) //only take ignore files which is suitable for all selected files
    }

    val additionalActions = createAdditionalActions(project, selectedFiles, unversionedFiles)
    if (resultedIgnoreFiles.isNotEmpty()) {
      actions = resultedIgnoreFiles.toActions(project, additionalActions.size)
    }
    else {
      actions = listOfNotNull(createNewIgnoreFileAction(project, selectedFiles))
    }

    if (additionalActions.isNotEmpty()) {
      actions += additionalActions
    }

    isPopup = actions.size > 1
    presentation.isVisible = actions.isNotEmpty()
  }

  protected open fun createAdditionalActions(project: Project,
                                             selectedFiles: List<VirtualFile>,
                                             unversionedFiles: List<VirtualFile>): List<AnAction> = emptyList()

  override fun canBePerformed(context: DataContext) = actions.size == 1

  override fun actionPerformed(e: AnActionEvent) {
    actions.firstOrNull()?.actionPerformed(e)
  }

  override fun getChildren(e: AnActionEvent?) = actions.toTypedArray()

  private fun filterSelectedFiles(project: Project, files: List<VirtualFile>) =
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

  private fun Collection<VirtualFile>.toActions(project: Project, additionalActionsSize: Int) =
    map { file ->
      IgnoreFileAction(file).apply {
        templatePresentation.apply {
          icon = ignoreFileType.icon
          text = file.toTextRepresentation(project, this@toActions.size + additionalActionsSize)
        }
      }
    }

  private fun createNewIgnoreFileAction(project: Project, selectedFiles: List<VirtualFile>): AnAction? {
    val filename = ignoreFileType.ignoreLanguage.filename
    val projectRoot = project.getProjectRoot()
    if (projectRoot == null || projectRoot.findChild(filename) != null) return null
    if (selectedFiles.any { !VfsUtil.isAncestor(projectRoot, it, true) }) return null //trying to ignore some parent file of the root ignore file
    val rootVcs = VcsUtil.getVcsFor(project, projectRoot) ?: return null
    val ignoredFileContentProvider = VcsImplUtil.findIgnoredFileContentProvider(rootVcs) ?: return null
    if (ignoredFileContentProvider.fileName != filename) return null

    return CreateNewIgnoreFileAction(filename, projectRoot).apply {
      templatePresentation.apply {
        icon = ignoreFileType.icon

        text = message("vcs.add.to.ignore.file.action.group.text", filename)
      }
    }
  }

  private fun VirtualFile.toTextRepresentation(project: Project, size: Int): String {
    if (size == 1) return message("vcs.add.to.ignore.file.action.group.text", ignoreFileType.ignoreLanguage.filename)
    val projectRoot = project.getProjectRoot() ?: return name

    return VfsUtil.getRelativePath(this, projectRoot) ?: name
  }

  private fun Project.getProjectRoot() =
    if (isDirectoryBased) stateStore.projectConfigDir?.let(LocalFileSystem.getInstance()::findFileByPath)?.parent
    else projectFile?.parent
}
