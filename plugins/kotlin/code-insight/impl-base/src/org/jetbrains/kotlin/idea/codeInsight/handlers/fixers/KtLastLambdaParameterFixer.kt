// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class KtLastLambdaParameterFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    @OptIn(KtAllowAnalysisOnEdt::class, KtAllowAnalysisFromWriteAction::class)
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, element: PsiElement) {
        val callElement = element as? KtCallElement ?: return

        val isFunctionType = allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                analyze(callElement) {
                    val functionCall = callElement.resolveCall()?.singleFunctionCallOrNull() ?: return
                    val valueParameters = functionCall.symbol.valueParameters
                    if (functionCall.argumentMapping.size != valueParameters.size - 1) return
                    valueParameters.lastOrNull()?.returnType is KtFunctionalType
                }
            }
        }

        if (isFunctionType) {
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
