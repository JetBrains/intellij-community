// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.ui.VcsLogUiHolder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object VcsLogEditorUtil {
  internal fun getLogIds(editor: FileEditor): Set<String> {
    return VcsLogUiHolder.getLogUis(editor.component).mapTo(mutableSetOf(), VcsLogUiEx::getId)
  }

  internal fun findSelectedLogIds(project: Project): Set<String> {
    return FileEditorManager.getInstance(project).selectedEditors.flatMapTo(mutableSetOf(), ::getLogIds)
  }

  internal fun selectLogUi(project: Project, ui: VcsLogUi): Boolean {
    val fileEditorManager = FileEditorManager.getInstance(project)
    val editor = fileEditorManager.findLogFile(ui) ?: return false
    return fileEditorManager.openFile(editor, true, true).isNotEmpty()
  }

  private fun FileEditorManager.findLogFile(ui: VcsLogUi): VirtualFile? {
    return allEditors.first { getLogIds(it).contains(ui.id) }?.file
  }

  internal fun closeLogTabs(project: Project, editorTabIds: List<String>): Boolean {
    if (editorTabIds.isEmpty()) return true
    val tabsToClose = editorTabIds.toMutableSet()

    val editorManager = FileEditorManager.getInstance(project)

    val editorsToIdsMap = editorManager.allEditors.asIterable().filter {
      getLogIds(it).intersect(tabsToClose).isNotEmpty()
    }

    for (logEditor in editorsToIdsMap) {
      val ids = logEditor.disposeLogUis()
      ApplicationManager.getApplication().invokeLater({ editorManager.closeFile(logEditor.file!!) }, ModalityState.nonModal(),
                                                      { project.isDisposed })
      tabsToClose.removeAll(ids.toHashSet())
    }
    return tabsToClose.isEmpty()
  }

  internal fun FileEditor.disposeLogUis(): List<String> {
    val logUis = VcsLogUiHolder.getLogUis(component)
    val disposedIds = logUis.map { it.id }
    if (logUis.isNotEmpty()) {
      component.removeAll()
      logUis.forEach(Disposer::dispose)
    }
    return disposedIds
  }
}