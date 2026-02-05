// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService

internal class KotlinGradleDependenciesAutoPopupHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (!useDependencyCompletionService()) return Result.CONTINUE
    if (!FileUtilRt.extensionEquals(file.name, "gradle.kts") || charTyped != '"') return Result.CONTINUE

    val offset = editor.caretModel.offset
    AutoPopupController.getInstance(project).scheduleAutoPopup(editor) { psiFile ->
      val element = psiFile.findElementAt(offset) ?: return@scheduleAutoPopup false
      if (!insideScriptBlockPattern(DEPENDENCIES).accepts(element)) return@scheduleAutoPopup false
      element.isSingleDependencyArgument()
      || element.isDependencyArgument(exclude)
      || element.isPositionalOrNamedDependencyArgument()
    }

    return Result.CONTINUE
  }
}
