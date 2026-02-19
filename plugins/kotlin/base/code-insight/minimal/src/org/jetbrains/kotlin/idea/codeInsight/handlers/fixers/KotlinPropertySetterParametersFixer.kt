// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.handlers.fixers

import com.intellij.lang.ASTNode
import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.psiUtil.endOffset


class KotlinPropertySetterParametersFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, psiElement: PsiElement) {
        if (psiElement !is KtPropertyAccessor) return

        if (!psiElement.isSetter) return

        val parameter = psiElement.parameter

        if (!parameter?.text.isNullOrBlank() && psiElement.rightParenthesis != null) return

        //setter without parameter and body is valid
        if (psiElement.namePlaceholder.endOffset == psiElement.endOffset) return

        val doc = editor.document

        val leftParenthesis = psiElement.leftParenthesis ?: psiElement.parameterList?.leftParenthesis

        val parameterOffset = (leftParenthesis?.node?.startOffset ?: return) + 1

        if (parameter?.text.isNullOrBlank()) {
            if (psiElement.rightParenthesis == null) {
                doc.insertString(parameterOffset, "value)")
            } else {
                doc.insertString(parameterOffset, "value")
            }
        } else if (psiElement.rightParenthesis == null) {
            doc.insertString(parameterOffset + parameter!!.text.length, ")")
        }
    }

    private val KtPropertyAccessor.leftParenthesis: ASTNode?
        get() = node.findChildByType(KtTokens.LPAR)

}

