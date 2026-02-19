// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Suppress the inspection that requires composable function names to start with a lower case letter.
 *
 * Forked from `com.android.tools.compose.ComposeSuppressor`
 */
internal class ComposableNamingInspectionSuppressor : InspectionSuppressor {
  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    return toolId.isFunctionNameTool() &&
           element.language == KotlinLanguage.INSTANCE &&
           element.node.elementType == KtTokens.IDENTIFIER &&
           element.parent.isComposableFunction()
  }

  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> = SuppressQuickFix.EMPTY_ARRAY
}

private fun String.isFunctionNameTool(): Boolean = this == "FunctionName" || this == "TestFunctionName"
