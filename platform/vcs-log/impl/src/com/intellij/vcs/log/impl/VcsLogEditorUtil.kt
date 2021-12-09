// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.ui.VcsLogUiEx

internal fun getLogIds(editor: FileEditor): Set<String> =
  VcsLogPanel.getLogUis(editor.component).mapTo(mutableSetOf(), VcsLogUiEx::getId)

internal fun findSelectedLogIds(project: Project): Set<String> {
  return FileEditorManager.getInstance(project).selectedEditors.flatMapTo(mutableSetOf(), ::getLogIds)
}

internal fun <T : VcsLogUiEx> findVcsLogUi(editors: Array<FileEditor>, clazz: Class<T>): T? {
  return editors.asSequence().flatMap { VcsLogPanel.getLogUis(it.component) }.filterIsInstance(clazz).firstOrNull()
}

internal fun updateTabName(project: Project, ui: VcsLogUiEx) {
  val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
  val file = fileEditorManager.allEditors.first { getLogIds(it).contains(ui.id) }?.file
  file?.let { fileEditorManager.updateFilePresentation(it) }
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
    ApplicationManager.getApplication().invokeLater({ editorManager.closeFile(logEditor.file!!) }, ModalityState.NON_MODAL,
                                                    { project.isDisposed })
    tabsToClose.removeAll(ids)
  }
  return tabsToClose.isEmpty()
}

internal fun FileEditor.disposeLogUis(): List<String> {
  val logUis = VcsLogPanel.getLogUis(component)
  val disposedIds = logUis.map { it.id }
  if (logUis.isNotEmpty()) {
    component.removeAll()
    logUis.forEach(Disposer::dispose)
  }
  return disposedIds
}