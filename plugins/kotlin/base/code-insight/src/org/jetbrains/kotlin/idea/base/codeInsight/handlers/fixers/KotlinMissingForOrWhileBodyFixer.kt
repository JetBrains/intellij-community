// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtWhileExpression

class KotlinMissingForOrWhileBodyFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, element: PsiElement) {
        if (!(element is KtForExpression || element is KtWhileExpression)) return
        val loopExpression = element as KtLoopExpression

        val doc = editor.document

        val body = loopExpression.body
        if (body is KtBlockExpression) return

        if (!loopExpression.isValidLoopCondition()) return

        if (body != null && body.startLine(doc) == loopExpression.startLine(doc)) return

        val rParen = loopExpression.rightParenthesis ?: return

        doc.insertString(rParen.range.end, "{}")
    }

    private fun KtLoopExpression.isValidLoopCondition() = leftParenthesis != null && rightParenthesis != null
}

