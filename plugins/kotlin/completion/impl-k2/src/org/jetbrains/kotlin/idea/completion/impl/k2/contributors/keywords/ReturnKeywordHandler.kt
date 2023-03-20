// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.keywords

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.completion.createKeywordElement
import org.jetbrains.kotlin.idea.completion.createKeywordElementWithSpace
import org.jetbrains.kotlin.idea.completion.implCommon.keywords.isInlineFunctionCall
import org.jetbrains.kotlin.idea.completion.isLikelyInPositionForReturn
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandler
import org.jetbrains.kotlin.idea.completion.labelNameToTail
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

/**
 * Implementation in K1: [org.jetbrains.kotlin.idea.completion.returnExpressionItems]
 */
internal object ReturnKeywordHandler : CompletionKeywordHandler<KtAnalysisSession>(KtTokens.RETURN_KEYWORD) {
    @OptIn(ExperimentalStdlibApi::class)
    override fun KtAnalysisSession.createLookups(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ): Collection<LookupElement> {
        if (expression == null) return emptyList()
        val result = mutableListOf<LookupElement>()

        for (parent in expression.parentsWithSelf.filterIsInstance<KtDeclarationWithBody>()) {
            val returnType = parent.getReturnKtType()
            if (parent is KtFunctionLiteral) {
                val (label, call) = parent.findLabelAndCall()
                if (label != null) {
                    addAllReturnVariants(result, returnType, label)
                }

                // check if the current function literal is inlined and stop processing outer declarations if it's not
                if (!isInlineFunctionCall(call)) {
                    break
                }
            } else {
                if (parent.hasBlockBody()) {
                    addAllReturnVariants(
                        result,
                        returnType,
                        label = null,
                        isLikelyInPositionForReturn(expression, parent, returnType.isUnit)
                    )
                }
                break
            }
        }

        return result
    }

    private fun KtAnalysisSession.addAllReturnVariants(
        result: MutableList<LookupElement>,
        returnType: KtType,
        label: Name?,
        isLikelyInPositionForReturn: Boolean = false
    ) {
        val isUnit = returnType.isUnit
        result.add(createKeywordElementWithSpace("return", tail = label?.labelNameToTail().orEmpty(), addSpaceAfter = !isUnit).also {
            it.isReturnAtHighlyLikelyPosition = isLikelyInPositionForReturn
        })
        getExpressionsToReturnByType(returnType).mapTo(result) { returnTarget ->
            val lookupElement = if (label != null || returnTarget.addToLookupElementTail) {
                createKeywordElement("return", tail = "${label.labelNameToTail()} ${returnTarget.expressionText}")
            } else {
                createKeywordElement("return ${returnTarget.expressionText}")
            }
            lookupElement.isReturnAtHighlyLikelyPosition = isLikelyInPositionForReturn
            lookupElement
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun KtAnalysisSession.getExpressionsToReturnByType(returnType: KtType): List<ExpressionTarget> = buildList {
        if (returnType.canBeNull) {
            add(ExpressionTarget("null", addToLookupElementTail = false))
        }

        fun emptyListShouldBeSuggested(): Boolean =
            returnType.isClassTypeWithClassId(StandardClassIds.Collection)
                    || returnType.isClassTypeWithClassId(StandardClassIds.List)
                    || returnType.isClassTypeWithClassId(StandardClassIds.Iterable)

        when {
            returnType.isClassTypeWithClassId(StandardClassIds.Boolean) -> {
                add(ExpressionTarget("true", addToLookupElementTail = false))
                add(ExpressionTarget("false", addToLookupElementTail = false))
            }
            emptyListShouldBeSuggested() -> {
                add(ExpressionTarget("emptyList()", addToLookupElementTail = true))
            }
            returnType.isClassTypeWithClassId(StandardClassIds.Set) -> {
                add(ExpressionTarget("emptySet()", addToLookupElementTail = true))
            }
        }
    }

    var LookupElement.isReturnAtHighlyLikelyPosition: Boolean by NotNullableUserDataProperty(
        Key("KOTLIN_IS_RETURN_AT_HIGHLY_LIKELY_POSITION"),
        false
    )
}

private data class ExpressionTarget(
    val expressionText: String,
    /**
     * FE10 completion sometimes add return target to LookupElement tails and sometimes not,
     * To ensure consistency (at least for tests) we need to do it to
     */
    val addToLookupElementTail: Boolean
)