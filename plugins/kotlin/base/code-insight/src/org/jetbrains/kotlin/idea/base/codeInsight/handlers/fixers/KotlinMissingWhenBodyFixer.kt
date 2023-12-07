// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtWhenExpression

class KotlinMissingWhenBodyFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, element: PsiElement) {
        if (element !is KtWhenExpression) return

        val doc = editor.document

        val openBrace = element.openBrace
        val closeBrace = element.closeBrace

        if (openBrace == null && closeBrace == null && element.entries.isEmpty()) {
            val openBraceAfter = element.insertOpenBraceAfter()
            if (openBraceAfter != null) {
                doc.insertString(openBraceAfter.range.end, "{}")
            }
        }
    }

    private fun KtWhenExpression.insertOpenBraceAfter(): PsiElement? = when {
        rightParenthesis != null -> rightParenthesis
        subjectExpression != null -> null
        leftParenthesis != null -> null
        else -> whenKeyword
    }
}
