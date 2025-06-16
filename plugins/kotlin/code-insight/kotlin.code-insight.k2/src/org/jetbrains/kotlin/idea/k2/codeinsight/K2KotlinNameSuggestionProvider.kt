// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameValidator
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal class K2KotlinNameSuggestionProvider : KotlinNameSuggestionProvider() {

    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    override fun getReturnTypeNames(
        callable: KtCallableDeclaration,
        validator: KotlinNameValidator,
    ): List<String> = allowAnalysisOnEdt {
        allowAnalysisFromWriteAction {
            analyze(callable) {
                val type = callable.returnType
                if (!type.isUnitType && !type.isPrimitive) {
                    with(KotlinNameSuggester()) {
                        suggestTypeNames(type).filter { validator(it) }.toList()
                    }
                } else emptyList()
            }
        }
    }

    override fun getNameForArgument(argument: KtValueArgument): String? {
        val callElement = (argument.parent as? KtValueArgumentList)?.parent as? KtCallElement ?: return null
        val arg = argument.getArgumentExpression() ?: return null
        analyze(callElement) {
            val resolvedCall = callElement.resolveToCall()?.singleFunctionCallOrNull() ?: return null
            return resolvedCall.argumentMapping[arg]?.name?.asString()
        }
    }
}