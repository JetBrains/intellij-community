// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow.splitApi.actions

import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ProblemsViewEditorUtils {

  fun positionCaret(offset: Int, editor: Editor){
    if (offset >= 0) {
      editor.caretModel.moveToOffset(offset.coerceAtMost(editor.document.textLength))
    }
  }


  @RequiresReadLock
  //for the legacy, monolithic implementation of `ShowProblemsViewQuickFixesAction`
  fun getEditor(psi: PsiFile): Editor? {
    val project = psi.project
    val document = PsiDocumentManager.getInstance(project).getDocument(psi) ?: return null

    return getEditor(document, project)
  }

  @RequiresReadLock
  fun getEditor(file: VirtualFile, project: Project): Editor? {
    if (!file.isValid) return null

    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    return getEditor(document, project)
  }

  fun getEditor(document: Document, project: Project): Editor? {
    return ClientEditorManager.getCurrentInstance().editors(document, project).firstOrNull { !it.isViewer }
  }

  @RequiresEdt
  fun openEditorIfNeeded(file: VirtualFile, project: Project, editor: Editor): Editor? {
    if (editor.isDisposed) return null

    if (UIUtil.isShowing(editor.component)) {
      return editor
    }

    val manager = FileEditorManager.getInstance(project) ?: return null
    if (manager.allEditors.none { UIUtil.isAncestor(it.component, editor.component) }) {
      return null
    }

    if (!file.isValid) return null
    manager.openFile(file, false, true)

    return if (UIUtil.isShowing(editor.component)) editor else null
  }
}
