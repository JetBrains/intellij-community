// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import kotlin.math.min

abstract class MissingConditionFixer<T : PsiElement> : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, element: PsiElement) {
        val workElement = getElement(element) ?: return

        val doc = editor.document
        val lParen = getLeftParenthesis(workElement)
        val rParen = getRightParenthesis(workElement)
        val condition = getCondition(workElement)

        if (condition == null) {
            if (lParen == null || rParen == null) {
                var stopOffset = doc.getLineEndOffset(doc.getLineNumber(workElement.range.start))
                val then = getBody(workElement)
                if (then != null) {
                    stopOffset = min(stopOffset, then.range.start)
                }

                stopOffset = min(stopOffset, workElement.range.end)

                doc.replaceString(workElement.range.start, stopOffset, "$keyword ()")
                processor.registerUnresolvedError(workElement.range.start + "$keyword (".length)
            } else {
                processor.registerUnresolvedError(lParen.range.end)
            }
        } else {
            if (rParen == null) {
                doc.insertString(condition.range.end, ")")
            }
        }
    }

    abstract val keyword: String
    abstract fun getElement(element: PsiElement?): T?
    abstract fun getCondition(element: T): PsiElement?
    abstract fun getLeftParenthesis(element: T): PsiElement?
    abstract fun getRightParenthesis(element: T): PsiElement?
    abstract fun getBody(element: T): PsiElement?
}
