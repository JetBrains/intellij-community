// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespace

object AddArgumentNamesUtils {
    fun addArgumentName(element: KtValueArgument, argumentName: Name) {
        val argumentExpression = element.getArgumentExpression() ?: return

        val prevSibling = element.getPrevSiblingIgnoringWhitespace()
        if (prevSibling is PsiComment && """/\*\s*$argumentName\s*=\s*\*/""".toRegex().matches(prevSibling.text)) {
            prevSibling.delete()
        }

        val newArgument = KtPsiFactory(element).createArgument(argumentExpression, argumentName, element.getSpreadElement() != null)
        element.replace(newArgument)
    }

    fun addArgumentNames(argumentNames: Map<KtValueArgument, Name>) {
        for ((argument, name) in argumentNames) {
            addArgumentName(argument, name)
        }
    }

    /**
     * Associates each argument of [call] with its argument name if `argumentName = argument` is valid for all arguments. Optionally,
     * starts at [startArgument] if it's not `null`.
     */
    context(KtAnalysisSession)
    fun associateArgumentNamesStartingAt(
        call: KtCallElement,
        startArgument: KtValueArgument?
    ): Map<SmartPsiElementPointer<KtValueArgument>, Name>? {
        val resolvedCall = call.resolveCall()?.singleFunctionCallOrNull() ?: return null
        if (!resolvedCall.symbol.hasStableParameterNames) {
            return null
        }

        val arguments = call.valueArgumentList?.arguments ?: return null
        val argumentsExcludingPrevious = if (startArgument != null) arguments.dropWhile { it != startArgument } else arguments
        return argumentsExcludingPrevious
            .associateWith { getArgumentNameIfCanBeUsedForCalls(it, resolvedCall) ?: return null }
            .mapKeys { it.key.createSmartPointer() }
    }
}