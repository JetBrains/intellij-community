// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.util.ThreeState
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService

internal class KotlinGradleDependenciesAutoPopupHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (!useDependencyCompletionService()) return Result.CONTINUE
    if (!file.name.endsWith(".gradle.kts") || charTyped != '"') return Result.CONTINUE

    val offset = editor.caretModel.offset
    val element = file.findElementAt(offset) ?: return Result.CONTINUE
    if (!insideScriptBlockPattern(DEPENDENCIES).accepts(element)) return Result.CONTINUE
    if (element.elementType == KtTokens.CLOSING_QUOTE || element.elementType == KtTokens.REGULAR_STRING_PART) return Result.CONTINUE

    AutoPopupController.getInstance(project).scheduleAutoPopup(editor) { psiFile ->
      val element = psiFile.findElementAt(offset) ?: return@scheduleAutoPopup false
      element.isValidDependencyArgument()
    }

    return Result.CONTINUE
  }
}

internal class EnableAutoPopupInKotlinGradleDependencyString : CompletionConfidence() {
  override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
    if (!useDependencyCompletionService()) return ThreeState.UNSURE
    if (!psiFile.name.endsWith(".gradle.kts")) return ThreeState.UNSURE
    return if (contextElement.isValidDependencyArgument()) ThreeState.NO
    else ThreeState.UNSURE
  }
}

private fun PsiElement.isValidDependencyArgument() =
  isSingleDependencyArgumentInsideQuotes()
  || isExcludeArgument()
  || isPositionalOrNamedDependencyArgument()
  || isKotlinShortcutModuleArgument()
  || (isKotlinShortcutVersionArgument() && !kotlinShortcutModuleHasVersion())
