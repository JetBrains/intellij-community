// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.keywords

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.types.KaType
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
import org.jetbrains.kotlin.psi.KtDeclarationWithReturnType
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

/**
 * Implementation in K1: [org.jetbrains.kotlin.idea.completion.returnExpressionItems]
 */
internal object ReturnKeywordHandler : CompletionKeywordHandler<KaSession>(KtTokens.RETURN_KEYWORD) {
    context(KaSession)
    override fun createLookups(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ): Collection<LookupElement> {
        if (expression == null) return emptyList()
        val result = mutableListOf<LookupElement>()

        for (parent in expression.parentsWithSelf.filterIsInstance<KtDeclarationWithBody>()) {
            val returnType = (parent as? KtDeclarationWithReturnType)?.returnType ?: continue
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
                        isLikelyInPositionForReturn(expression, parent, returnType.isUnitType)
                    )
                }
                break
            }
        }

        return result
    }

    context(KaSession)
    private fun addAllReturnVariants(
        result: MutableList<LookupElement>,
        returnType: KaType,
        label: Name?,
        isLikelyInPositionForReturn: Boolean = false
    ) {
        val isUnit = returnType.isUnitType
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

    context(KaSession)
    private fun getExpressionsToReturnByType(returnType: KaType): List<ExpressionTarget> = buildList {
        if (returnType.canBeNull) {
            add(ExpressionTarget("null", addToLookupElementTail = false))
        }

        fun emptyListShouldBeSuggested(): Boolean =
            returnType.isClassType(StandardClassIds.Collection)
                    || returnType.isClassType(StandardClassIds.List)
                    || returnType.isClassType(StandardClassIds.Iterable)

        when {
            returnType.isClassType(StandardClassIds.Boolean) -> {
                add(ExpressionTarget("true", addToLookupElementTail = false))
                add(ExpressionTarget("false", addToLookupElementTail = false))
            }

            emptyListShouldBeSuggested() -> {
                add(ExpressionTarget("emptyList()", addToLookupElementTail = true))
            }

            returnType.isClassType(StandardClassIds.Set) -> {
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