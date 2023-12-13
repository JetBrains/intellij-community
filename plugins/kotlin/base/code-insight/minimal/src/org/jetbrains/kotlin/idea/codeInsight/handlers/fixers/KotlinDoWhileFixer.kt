// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDoWhileExpression

class KotlinDoWhileFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, psiElement: PsiElement) {
        if (psiElement !is KtDoWhileExpression) return

        val doc = editor.document
        val start = psiElement.range.start
        val body = psiElement.body

        val whileKeyword = psiElement.whileKeyword
        if (body == null) {
            if (whileKeyword == null) {
                doc.replaceString(start, start + "do".length, "do {} while()")
            } else {
                doc.insertString(start + "do".length, "{}")
            }
            return
        } else if (whileKeyword != null && body !is KtBlockExpression && body.startLine(doc) > psiElement.startLine(doc)) {
            doc.insertString(whileKeyword.range.start, "}")
            doc.insertString(start + "do".length, "{")

            return
        }

        if (psiElement.condition == null) {
            val lParen = psiElement.leftParenthesis
            val rParen = psiElement.rightParenthesis

            when {
                whileKeyword == null -> doc.insertString(psiElement.range.end, "while()")
                lParen == null && rParen == null -> {
                    doc.replaceString(whileKeyword.range.start, whileKeyword.range.end, "while()")
                }
                lParen != null -> processor.registerUnresolvedError(lParen.range.end)
            }
        }
    }
}
