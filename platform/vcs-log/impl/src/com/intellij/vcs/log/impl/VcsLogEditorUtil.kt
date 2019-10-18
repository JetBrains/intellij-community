// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.ui.VcsLogPanel

fun getLogId(editor: FileEditor): String? = (editor.component as? VcsLogPanel)?.getUi()?.id

fun findSelectedLogIds(project: Project): Set<String> {
  return FileEditorManager.getInstance(project).selectedEditors.mapNotNullTo(mutableSetOf(), ::getLogId)
}

fun closeLogTab(project: Project, tabId: String): Boolean {
  val editorManager = FileEditorManager.getInstance(project)
  val logFile = editorManager.allEditors.find { getLogId(it) == tabId }?.file ?: return false
  editorManager.closeFile(logFile)
  return true
}