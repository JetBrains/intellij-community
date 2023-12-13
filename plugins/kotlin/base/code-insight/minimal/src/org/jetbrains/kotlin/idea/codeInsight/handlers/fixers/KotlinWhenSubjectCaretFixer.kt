// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtWhenExpression

class KotlinWhenSubjectCaretFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, element: PsiElement) {
        if (element !is KtWhenExpression) return

        val lParen = element.leftParenthesis
        val rParen = element.rightParenthesis
        val subject = element.subjectExpression

        if (subject == null && lParen != null && rParen != null) {
            processor.registerUnresolvedError(lParen.range.end)
        }
    }
}
