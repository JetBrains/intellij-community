// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.toml

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService
import org.toml.lang.TomlLanguage

internal class GradleTomlDependenciesAutoPopupHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (!useDependencyCompletionService()) return Result.CONTINUE
    if (file.language !is TomlLanguage) return Result.CONTINUE
    if (charTyped != '"' && !charTyped.isLetterOrDigit() && charTyped != '-' && charTyped != '.' && charTyped != ':') {
      return Result.CONTINUE
    }

    val offset = editor.caretModel.offset
    AutoPopupController.getInstance(project).scheduleAutoPopup(editor) { psiFile ->
      val element = psiFile.findElementAt(offset) ?: return@scheduleAutoPopup false
      insideLibrariesTable().accepts(element)
    }

    return Result.CONTINUE
  }
}
