// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ignore.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.VcsBundle.messagePointer
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

open class IgnoreFileActionGroup(private val ignoreFileType: IgnoreFileType) :
  ActionGroup(
    messagePointer("vcs.add.to.ignore.file.action.group.text", ignoreFileType.ignoreLanguage.filename),
    messagePointer("vcs.add.to.ignore.file.action.group.description", ignoreFileType.ignoreLanguage.filename),
    { ignoreFileType.icon }
  ), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  protected open fun createAdditionalActions(
    project: Project,
    selectedFiles: List<VirtualFile>,
    unversionedFiles: List<VirtualFile>,
  ): List<AnAction> = emptyList()

  private fun createActionsFor(e: AnActionEvent): List<AnAction> {
    val selectedFiles = getSelectedFiles(e)

    val project = e.getData(CommonDataKeys.PROJECT)
    if (project == null) {
      return emptyList()
    }

    val unversionedFiles = ScheduleForAdditionAction.Manager.getUnversionedFiles(e, project).toList()
    if (unversionedFiles.isEmpty()) {
      return emptyList()
    }

    val ignoreFiles =
      filterSelectedFiles(project, selectedFiles).map { findSuitableIgnoreFiles(project, it) }.filterNot(Collection<*>::isEmpty)
    val resultedIgnoreFiles = ignoreFiles.flatten().toHashSet()

    for (files in ignoreFiles) {
      resultedIgnoreFiles.retainAll(files) //only take ignore files which is suitable for all selected files
    }

    val actions = mutableListOf<AnAction>()
    val additionalActions = createAdditionalActions(project, selectedFiles, unversionedFiles)
    if (resultedIgnoreFiles.isNotEmpty()) {
      actions += resultedIgnoreFiles.toActions(project, additionalActions.size)
    }
    else {
      actions += listOfNotNull(createNewIgnoreFileAction(project, selectedFiles))
    }

    if (additionalActions.isNotEmpty()) {
      actions += additionalActions
    }

    return actions
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val actions = createActionsFor(e)

    presentation.isPopupGroup = actions.size > 1
    presentation.isPerformGroup = actions.size == 1
    e.presentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, e.presentation.isPerformGroup)
    presentation.isEnabledAndVisible = actions.isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val action = createActionsFor(e).singleOrNull() ?: return
    action.actionPerformed(e)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (e == null) return EMPTY_ARRAY
    return createActionsFor(e).toTypedArray()
  }

  private fun filterSelectedFiles(project: Project, files: List<VirtualFile>): List<VirtualFile> {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val changeListManager = ChangeListManager.getInstance(project)
    return files.filter { file -> vcsManager.getVcsFor(file) != null && !changeListManager.isIgnoredFile(file) }
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
