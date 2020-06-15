// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.ui.editor.VCS_LOG_FILE_DISPLAY_NAME_GENERATOR
import com.intellij.vcs.log.ui.editor.VcsLogEditor
import com.intellij.vcs.log.ui.editor.VcsLogFile
import javax.swing.JComponent

internal fun getLogIds(editor: FileEditor): Set<String> =
  VcsLogContentUtil.getLogUis(editor.component).mapTo(mutableSetOf(), VcsLogUiEx::getId)

internal fun findSelectedLogIds(project: Project): Set<String> {
  return FileEditorManager.getInstance(project).selectedEditors.flatMapTo(mutableSetOf(), ::getLogIds)
}

internal fun getExistingLogIds(project: Project): Set<String> {
  return FileEditorManager.getInstance(project).allEditors.flatMapTo(mutableSetOf(), ::getLogIds)
}

internal fun updateTabName(project: Project, ui: VcsLogUiEx) {
  val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
  val file = fileEditorManager.allEditors.first { getLogIds(it).contains(ui.id) }?.file
  file?.let { fileEditorManager.updateFilePresentation(it) }
}

internal fun <U : VcsLogUiEx> openLogTab(project: Project, logManager: VcsLogManager, name: String,
                                         factory: VcsLogManager.VcsLogUiFactory<U>, focus: Boolean): U =
  openLogTab(project, logManager, name, VcsLogTabsManager::generateDisplayName, factory, focus)

internal fun <U : VcsLogUiEx> openLogTab(project: Project,
                                         logManager: VcsLogManager,
                                         name: String,
                                         generateDisplayName: (U) -> String,
                                         factory: VcsLogManager.VcsLogUiFactory<U>,
                                         focus: Boolean): U {
  val logUi = logManager.createLogUi(factory, VcsLogManager.LogWindowKind.EDITOR)

  createAndOpenLogFile(project, logManager, VcsLogPanel(logManager, logUi), listOf(logUi), name, { generateDisplayName(logUi) }, focus)
  return logUi
}

fun <U : VcsLogUiEx> createAndOpenLogFile(project: Project,
                                          logManager: VcsLogManager,
                                          rootComponent: JComponent,
                                          logUis: List<U>,
                                          name: String,
                                          generateDisplayName: (List<VcsLogUiEx>) -> String,
                                          focus: Boolean) {
  val file = VcsLogFile(rootComponent, logUis, name).apply {
    putUserData(VCS_LOG_FILE_DISPLAY_NAME_GENERATOR, generateDisplayName)
  }
  invokeLater(ModalityState.NON_MODAL) { FileEditorManager.getInstance(project).openFile(file, focus) }

  logManager.scheduleInitialization()
}

internal fun closeLogTabs(project: Project, editorTabIds: List<String>): Boolean {
  if (editorTabIds.isEmpty()) return true
  val tabsToClose = editorTabIds.toMutableSet()

  val editorManager = FileEditorManager.getInstance(project)

  val editorsToIdsMap = editorManager.allEditors.asIterable().associateWith {
    getLogIds(it).intersect(tabsToClose)
  }.filterValues { logIds -> logIds.isNotEmpty() }

  for ((logEditor, ids) in editorsToIdsMap) {
    (logEditor as? VcsLogEditor)?.disposeLogUis()
    ApplicationManager.getApplication().invokeLater({ editorManager.closeFile(logEditor.file!!) }, ModalityState.NON_MODAL,
                                                    { project.isDisposed })
    tabsToClose.removeAll(ids)
  }
  return tabsToClose.isEmpty()
}