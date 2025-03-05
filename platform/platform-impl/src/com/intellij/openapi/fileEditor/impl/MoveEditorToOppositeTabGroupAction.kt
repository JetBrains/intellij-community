// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile

private open class MoveEditorToOppositeTabGroupAction(
  private val closeSource: Boolean,
) : AnAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  @Suppress("unused")
  constructor() : this(closeSource = true)

  override fun actionPerformed(event: AnActionEvent) {
    val dataContext = event.dataContext
    val vFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return

    val window = EditorWindow.DATA_KEY.getData(dataContext) ?: return
    val siblings = window.getSiblings()
    if (siblings.size != 1) {
      return
    }

    val entry = window.selectedComposite?.currentStateAsFileEntry()
    vFile.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, true)
    if (closeSource) {
      window.closeFile(file = vFile, disposeIfNeeded = true, transferFocus = false)
    }
    (FileEditorManagerEx.getInstanceEx(project) as FileEditorManagerImpl).openFileImpl(
      window = siblings.get(0),
      _file = vFile,
      entry = entry,
      options = FileEditorOpenOptions(requestFocus = true),
    )
    vFile.putUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN, null)
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val dataContext = e.dataContext
    val enabled = isEnabled(
      vFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext) ?: return,
      window = EditorWindow.DATA_KEY.getData(dataContext) ?: return,
    )
    presentation.setEnabled(enabled)
    if (e.isFromContextMenu) {
      presentation.setVisible(enabled)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private fun isEnabled(vFile: VirtualFile, window: EditorWindow): Boolean {
    if (!closeSource && FileEditorManagerImpl.forbidSplitFor(vFile)) {
      return false
    }
    return window.getSiblings().size == 1
  }
}

private class OpenEditorInOppositeTabGroupAction : MoveEditorToOppositeTabGroupAction(closeSource = false)