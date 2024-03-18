// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import kotlin.math.min

class KotlinCatchParameterFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, psiElement: PsiElement) {
        if (psiElement !is KtCatchClause) return

        val catchEnd = psiElement.node.findChildByType(KtTokens.CATCH_KEYWORD)!!.textRange!!.endOffset

        val parameterList = psiElement.parameterList
        if (parameterList == null || parameterList.node.findChildByType(KtTokens.RPAR) == null) {
            val endOffset = min(psiElement.endOffset, psiElement.catchBody?.startOffset ?: Int.MAX_VALUE)
            val parameter = parameterList?.parameters?.firstOrNull()?.text ?: ""
            editor.document.replaceString(catchEnd, endOffset, "($parameter)")
            processor.registerUnresolvedError(endOffset - 1)
        } else if (parameterList.parameters.firstOrNull()?.text.isNullOrBlank()) {
            processor.registerUnresolvedError(parameterList.startOffset + 1)
        }
    }
}