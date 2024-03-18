// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorCustomizer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.CoroutineScope

private class FloatingCodeToolbarEditorCustomizer: TextEditorCustomizer {
  override fun customize(textEditor: TextEditor) {
    val editor = textEditor.editor
    val project = editor.project ?: return
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
    val languages = file.viewProvider.languages
    if (languages.none { language -> FloatingToolbarCustomizer.findActionGroupFor(language) != null }) return
    val coroutineScope = FloatingCodeToolbarScope.createChildScope(project)
    val toolbar = CodeFloatingToolbar(editor, coroutineScope)
    Disposer.register(textEditor, toolbar)
  }
}

@Service(Service.Level.PROJECT)
private class FloatingCodeToolbarScope(private val coroutineScope: CoroutineScope) {
  companion object {
    fun createChildScope(project: Project): CoroutineScope {
      return project.service<FloatingCodeToolbarScope>().coroutineScope.childScope()
    }
  }
}
