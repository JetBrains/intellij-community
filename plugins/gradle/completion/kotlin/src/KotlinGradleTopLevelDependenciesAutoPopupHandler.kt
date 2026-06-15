// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService

/**
 * Re-triggers autopopup as a dependency coordinate is typed directly (without quotes) at the top level of a
 * `dependencies { }` block, e.g. `dependencies { juni<caret> }`.
 */
internal class KotlinGradleTopLevelDependenciesAutoPopupHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (!useDependencyCompletionService()) return Result.CONTINUE
    if (!file.name.endsWith(".gradle.kts")) return Result.CONTINUE
    if (!charTyped.isLetterOrDigit() && charTyped != '.' && charTyped != '-' && charTyped != '_' && charTyped != ':') {
      return Result.CONTINUE
    }

    val offset = editor.caretModel.offset
    AutoPopupController.getInstance(project).scheduleAutoPopup(editor) { psiFile ->
      val element = psiFile.findElementAt(offset) ?: return@scheduleAutoPopup false
      element.isOnTheTopLevelOfScriptBlock(DEPENDENCIES)
    }

    return Result.CONTINUE
  }
}
