// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.handlers.fixers

import com.intellij.lang.SmartEnterProcessorWithFixers
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.codeInsight.handlers.KotlinSmartEnterHandler
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.psiUtil.endOffset

class KtLastLambdaParameterFixer : SmartEnterProcessorWithFixers.Fixer<KotlinSmartEnterHandler>() {
    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    override fun apply(editor: Editor, processor: KotlinSmartEnterHandler, element: PsiElement) {
        val callElement = element as? KtCallElement ?: return

        val isFunctionType = allowAnalysisFromWriteAction {
            allowAnalysisOnEdt {
                analyze(callElement) {
                    val functionCall = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return
                    val valueParameters = functionCall.symbol.valueParameters
                    if (functionCall.argumentMapping.size != valueParameters.size - 1) return
                    valueParameters.lastOrNull()?.returnType is KaFunctionType
                }
            }
        }
       if (callElement.lambdaArguments.isNotEmpty()) return

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
