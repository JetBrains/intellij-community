// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtTryExpression

class KotlinTryBodyFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, psiElement: PsiElement) {
        if (psiElement !is KtTryExpression) return

        if (psiElement.tryBlock.lBrace == null) {
            val offset = psiElement.node.findChildByType(KtTokens.TRY_KEYWORD)?.textRange?.endOffset ?: return
            editor.document.insertString(offset, "{}")
        }
    }
}