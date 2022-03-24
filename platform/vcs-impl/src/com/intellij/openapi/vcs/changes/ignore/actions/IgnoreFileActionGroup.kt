// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.Nls
import kotlin.streams.toList

open class IgnoreFileActionGroup(private val ignoreFileType: IgnoreFileType) :
  ActionGroup(
    message("vcs.add.to.ignore.file.action.group.text", ignoreFileType.ignoreLanguage.filename),
    message("vcs.add.to.ignore.file.action.group.description", ignoreFileType.ignoreLanguage.filename),
    ignoreFileType.icon
  ), DumbAware, UpdateInBackground {

  private var actions: Collection<AnAction> = emptyList()

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

    presentation.isPopupGroup = actions.size > 1
    presentation.isPerformGroup = actions.size == 1
    presentation.isVisible = actions.isNotEmpty()
  }

  protected open fun createAdditionalActions(project: Project,
                                             selectedFiles: List<VirtualFile>,
                                             unversionedFiles: List<VirtualFile>): List<AnAction> = emptyList()

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

  private fun Collection<VirtualFile>.toActions(project: Project, additionalActionsSize: Int): Collection<AnAction> {
    val projectDir = project.guessProjectDir()
    return map { file ->
      IgnoreFileAction(file).apply {
        templatePresentation.apply {
          icon = ignoreFileType.icon
          text = file.toTextRepresentation(project, projectDir, this@toActions.size + additionalActionsSize)
        }
      }
    }
  }

  private fun createNewIgnoreFileAction(project: Project, selectedFiles: List<VirtualFile>): AnAction? {
    val filename = ignoreFileType.ignoreLanguage.filename
    val (rootVcs, commonIgnoreFileRoot) = getCommonIgnoreFileRoot(selectedFiles, project) ?: return null
    if (rootVcs == null) return null
    if (commonIgnoreFileRoot.findChild(filename) != null) return null
    val ignoredFileContentProvider = VcsImplUtil.findIgnoredFileContentProvider(rootVcs) ?: return null
    if (ignoredFileContentProvider.fileName != filename) return null

    return CreateNewIgnoreFileAction(filename, commonIgnoreFileRoot).apply {
      templatePresentation.apply {
        icon = ignoreFileType.icon
        text = message("vcs.add.to.ignore.file.action.group.text", filename)
      }
    }
  }

  private fun VirtualFile.toTextRepresentation(project: Project, projectDir: VirtualFile?, size: Int): @Nls String {
    if (size == 1) {
      return message("vcs.add.to.ignore.file.action.group.text", ignoreFileType.ignoreLanguage.filename)
    }
    val projectRootOrVcsRoot = projectDir ?: VcsUtil.getVcsRootFor(project, this) ?: return name
    return VfsUtil.getRelativePath(this, projectRootOrVcsRoot) ?: name
  }

  private operator fun VcsRoot.component1() = vcs
  private operator fun VcsRoot.component2() = path
}

private fun getCommonIgnoreFileRoot(files: Collection<VirtualFile>, project: Project): VcsRoot? {
  val first = files.firstOrNull() ?: return null
  val vcsManager = ProjectLevelVcsManager.getInstance(project)
  val commonVcsRoot = vcsManager.getVcsRootObjectFor(first) ?: return null
  if (first == commonVcsRoot.path) {
    // trying to ignore vcs root itself
    return null
  }

  val haveCommonRoot = files.asSequence().drop(1).all {
    it != commonVcsRoot.path && vcsManager.getVcsRootObjectFor(it) == commonVcsRoot
  }

  return if (haveCommonRoot) commonVcsRoot else null
}
