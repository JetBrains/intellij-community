// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.vcs.log.ui.editor.VcsLogEditor
import com.intellij.vcs.log.ui.editor.VcsLogFile

fun getLogId(editor: FileEditor): String? = VcsLogContentUtil.getLogUi(editor.component)?.id

fun findSelectedLogIds(project: Project): Set<String> {
  return FileEditorManager.getInstance(project).selectedEditors.mapNotNullTo(mutableSetOf(), ::getLogId)
}

fun getExistingLogIds(project: Project): Set<String> {
  return FileEditorManager.getInstance(project).allEditors.mapNotNullTo(mutableSetOf(), ::getLogId)
}

fun updateTabName(project: Project, ui: VcsLogUiEx) {
  val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
  val file = fileEditorManager.allEditors.first { getLogId(it) == ui.id }?.file
  file?.let { fileEditorManager.updateFilePresentation(it) }
}

fun <U : VcsLogUiEx> openLogTab(project: Project, logManager: VcsLogManager, name: String,
                                factory: VcsLogManager.VcsLogUiFactory<U>, focus: Boolean): U {
  val logUi = logManager.createLogUi(factory, VcsLogManager.LogWindowKind.EDITOR)

  val file = VcsLogFile(VcsLogPanel(logManager, logUi), name)
  invokeLater(ModalityState.NON_MODAL) { FileEditorManager.getInstance(project).openFile(file, focus) }

  logManager.scheduleInitialization()
  return logUi
}

fun closeLogTab(project: Project, tabId: String): Boolean {
  val editorManager = FileEditorManager.getInstance(project)

  val logEditor = editorManager.allEditors.find { getLogId(it) == tabId } ?: return false
  val logFile = logEditor.file ?: return false

  (logEditor as? VcsLogEditor)?.beforeEditorClose()
  ApplicationManager.getApplication().invokeLater({ editorManager.closeFile(logFile) }, ModalityState.NON_MODAL, { project.isDisposed })
  return true
}