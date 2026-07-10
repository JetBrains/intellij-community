// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow.splitApi.actions

import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ProblemsViewEditorUtils {

  fun positionCaret(offset: Int, editor: Editor){
    if (offset >= 0) {
      editor.caretModel.moveToOffset(offset.coerceAtMost(editor.document.textLength))
    }
  }

  fun getEditor(psi: PsiFile, showEditor: Boolean): Editor? {
    val file = psi.virtualFile ?: return null
    val document = PsiDocumentManager.getInstance(psi.project).getDocument(psi) ?: return null
    val editor = ClientEditorManager.getCurrentInstance().editors(document, psi.project).firstOrNull { !it.isViewer } ?: return null
    if (!showEditor || UIUtil.isShowing(editor.component)) {
      return editor
    }

    val manager = FileEditorManager.getInstance(psi.project) ?: return null
    if (manager.allEditors.none { UIUtil.isAncestor(it.component, editor.component) }) {
      return null
    }

    manager.openFile(file, false, true)
    return if (UIUtil.isShowing(editor.component)) editor else null
  }

  fun getEditor(file: VirtualFile, project: Project, showEditor: Boolean): Editor? {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    val editor = ClientEditorManager.getCurrentInstance().editors(document, project).firstOrNull { !it.isViewer } ?: return null

    if (!showEditor || UIUtil.isShowing(editor.component)) {
      return editor
    }

    val manager = FileEditorManager.getInstance(project) ?: return null
    if (manager.allEditors.none { UIUtil.isAncestor(it.component, editor.component) }) {
      return null
    }

    manager.openFile(file, false, true)
    return if (UIUtil.isShowing(editor.component)) editor else null
  }
}