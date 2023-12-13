// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtIfExpression

class KotlinMissingIfBranchFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, element: PsiElement) {
        if (element !is KtIfExpression) return

        val document = editor.document
        val elseBranch = element.`else`
        val elseKeyword = element.elseKeyword

        if (elseKeyword != null) {
            if (elseBranch == null || elseBranch !is KtBlockExpression && elseBranch.startLine(document) > elseKeyword.startLine(document)) {
                document.insertString(elseKeyword.range.end, "{}")
                return
            }
        }

        val thenBranch = element.then
        if (thenBranch is KtBlockExpression) return

        val rParen = element.rightParenthesis ?: return

        var transformingOneLiner = false
        if (thenBranch != null && thenBranch.startLine(document) == rParen.startLine(document)) {
            if (element.condition != null) return
            transformingOneLiner = true
        }

        val probablyNextStatementParsedAsThen = elseKeyword == null && elseBranch == null && !transformingOneLiner

        if (thenBranch == null || probablyNextStatementParsedAsThen) {
            document.insertString(rParen.range.end, "{}")
        } else {
            document.insertString(rParen.range.end, "{")
            document.insertString(thenBranch.range.end + 1, "}")
        }
    }
}
