// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class KotlinPropertyAccessorBodyFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, psiElement: PsiElement) {
        if (psiElement !is KtPropertyAccessor) return

        if (psiElement.bodyExpression != null || psiElement.equalsToken != null) return

        val parentDeclaration = psiElement.getStrictParentOfType<KtDeclaration>()
        if (parentDeclaration is KtClassOrObject) {
            if (KtPsiUtil.isTrait(parentDeclaration) || psiElement.hasModifier(KtTokens.ABSTRACT_KEYWORD)) {
                return
            }
        }

        // accessor without parameter and body is valid
        if (psiElement.namePlaceholder.endOffset == psiElement.endOffset) return

        val doc = editor.document
        var endOffset = psiElement.range.end

        if (psiElement.isGetter && psiElement.rightParenthesis == null) {
            doc.insertString(endOffset, ")")
            endOffset++
        }

        if (psiElement.text?.last() == ';') {
            doc.deleteString(endOffset - 1, endOffset)
            endOffset--
        }

        doc.insertString(endOffset, "{\n}")
    }
}