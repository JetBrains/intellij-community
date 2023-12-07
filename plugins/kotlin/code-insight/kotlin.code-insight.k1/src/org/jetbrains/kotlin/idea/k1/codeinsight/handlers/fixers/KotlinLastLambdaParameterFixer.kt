// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k1.codeinsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.idea.base.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.idea.caches.resolve.allowResolveInDispatchThread
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class KotlinLastLambdaParameterFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, element: PsiElement) {
        if (element !is KtCallExpression) return

        val resolvedCall = allowResolveInDispatchThread { element.resolveToCall() } ?: return

        val valueParameters = resolvedCall.candidateDescriptor.valueParameters

        if (resolvedCall.valueArguments.size == valueParameters.size - 1) {
            val type = valueParameters.last().type
            if (type.isFunctionType) {
                val doc = editor.document

                var offset = element.endOffset
                if (element.valueArgumentList?.rightParenthesis == null) {
                    doc.insertString(offset, ")")
                    offset++
                }

                doc.insertString(offset, "{ }")
                processor.registerUnresolvedError(offset + 2)
                processor.commit(editor)
            }
        }
    }
}
