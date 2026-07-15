// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager.Companion.getInstance
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.JBIterable
import com.intellij.vcs.log.VcsLogFileHistoryProvider
import com.intellij.vcsUtil.VcsUtil

open class TabbedShowHistoryAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val context = e.dataContext

    val isVisible = project != null && getInstance(project).hasActiveVcss()
    val isEnabled = isEnabled(context)

    e.presentation.isEnabled = isEnabled
    e.presentation.isVisible = isVisible

    if (isEnabled && isVisible && VcsContextUtil.selectedFilePathsIterable(context).isEmpty()) {
      getEditorFile(context)?.let { editorFile ->
        e.presentation.text = ActionsBundle.message("action.Vcs.ShowTabbedFileHistory.for.file.text", editorFile.getName())
      }
    }
  }

  protected open fun isEnabled(context: DataContext): Boolean {
    val project = context.getData(CommonDataKeys.PROJECT) ?: return false

    val selectedFiles = getSelectedPaths(context)
      .take(MANY_CHANGES_THRESHOLD)
      .toList()

    if (selectedFiles.isEmpty()) return false

    val symlinkedPaths = getContextSymlinkedPaths(project, getSelectedFile(context))
    if (symlinkedPaths != null && canShowNewFileHistory(project, symlinkedPaths)) {
      return true
    }

    if (canShowNewFileHistory(project, selectedFiles)) {
      return selectedFiles.all { path -> AbstractVcs.fileInVcsByFileStatus(project, path) }
    }

    if (selectedFiles.size == 1) {
      val selectedPath = selectedFiles.firstOrNull() ?: return false
      val fileOrParent = getExistingFileOrParent(selectedPath) ?: return false

      if (canShowOldFileHistory(project, selectedPath, fileOrParent)) {
        return AbstractVcs.fileInVcsByFileStatus(project, selectedPath)
      }
    }

    return false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val symlinkedPaths = getContextSymlinkedPaths(project, getSelectedFile(e.dataContext))
    if (symlinkedPaths != null && canShowNewFileHistory(project, symlinkedPaths)) {
      showNewFileHistory(project, symlinkedPaths)
      return
    }

    val selectedFiles = getSelectedPaths(e.dataContext).toList()
    if (canShowNewFileHistory(project, selectedFiles)) {
      showNewFileHistory(project, selectedFiles)
      return
    }

    if (selectedFiles.size == 1) {
      val path = ContainerUtil.getOnlyItem(selectedFiles)
      val fileOrParent = getExistingFileOrParent(path) ?: return
      val vcs = ChangesUtil.getVcsForFile(fileOrParent, project) ?: return

      showOldFileHistory(project, vcs, path)
    }
  }

  private fun canShowOldFileHistory(project: Project, path: FilePath, fileOrParent: VirtualFile): Boolean {
    val vcs = ChangesUtil.getVcsForFile(fileOrParent, project)
    if (vcs == null) return false

    val provider = vcs.vcsHistoryProvider
    return provider != null &&
           (provider.supportsHistoryForDirectories() || !path.isDirectory()) &&
           provider.canShowHistoryFor(fileOrParent)
  }

  private fun canShowNewFileHistory(project: Project, paths: List<FilePath>): Boolean {
    val historyProvider = project.getService<VcsLogFileHistoryProvider?>(VcsLogFileHistoryProvider::class.java)
    return historyProvider != null && historyProvider.canShowFileHistory(paths, null)
  }

  private fun getExistingFileOrParent(selectedPath: FilePath): VirtualFile? {
    return ObjectUtils.chooseNotNull<VirtualFile?>(selectedPath.getVirtualFile(), selectedPath.getVirtualFileParent())
  }

  private fun getContextSymlinkedPaths(project: Project, file: VirtualFile?): List<FilePath>? {
    return VcsUtil.resolveSymlink(project, file)?.let { vcsFile ->
      listOf(VcsUtil.getFilePath(vcsFile))
    }
  }

  private fun showNewFileHistory(project: Project, paths: List<FilePath>) {
    val historyProvider = project.getService(VcsLogFileHistoryProvider::class.java)
    historyProvider.showFileHistory(paths, null)
  }

  private fun showOldFileHistory(project: Project, vcs: AbstractVcs, path: FilePath) {
    val provider = vcs.vcsHistoryProvider!!
    AbstractVcsHelper.getInstance(project).showFileHistory(provider, vcs.annotationProvider, path, vcs)
  }

  private fun getSelectedPaths(context: DataContext): JBIterable<FilePath> {
    val paths = VcsContextUtil.selectedFilePathsIterable(context)
    if (paths.isNotEmpty) return paths
    val file = getEditorFile(context) ?: return JBIterable.empty()
    return JBIterable.of(VcsUtil.getFilePath(file))
  }

  private fun getSelectedFile(context: DataContext): VirtualFile? {
    val file = VcsContextUtil.selectedFile(context)
    if (file != null) return file
    return getEditorFile(context)
  }

  private fun getEditorFile(context: DataContext): VirtualFile? {
    val project = context.getData(CommonDataKeys.PROJECT) ?: return null
    return FileEditorManager.getInstance(project).getSelectedFiles().firstOrNull().takeIf { it?.isInLocalFileSystem == true }
  }

  internal companion object {
    private const val MANY_CHANGES_THRESHOLD = 1000
  }
}
