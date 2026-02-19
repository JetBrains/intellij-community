// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon.keywords

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaContextParameterApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.completion.createKeywordElement
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandler
import org.jetbrains.kotlin.idea.completion.labelNameToTail
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

/**
 * Affected tests:
 * [org.jetbrains.kotlin.idea.completion.test.handlers.KeywordCompletionHandlerTestGenerated]
 * [org.jetbrains.kotlin.idea.completion.test.JvmBasicCompletionTestGenerated.Common.AutoPopup]
 * [org.jetbrains.kotlin.idea.completion.test.JSBasicCompletionTestGenerated.Common.AutoPopup]
 * [org.jetbrains.kotlin.idea.completion.test.KeywordCompletionTestGenerated]
 * [org.jetbrains.kotlin.idea.completion.test.weighers.BasicCompletionWeigherTestGenerated.Uncategorized]
 *
 * [org.jetbrains.kotlin.idea.fir.completion.FirKeywordCompletionTestGenerated]
 * [org.jetbrains.kotlin.idea.fir.completion.test.handlers.FirKeywordCompletionHandlerTestGenerated]
 * [org.jetbrains.kotlin.idea.fir.completion.wheigher.HighLevelWeigherTestGenerated.Uncategorized]
 */
class BreakContinueKeywordHandler(keyword: KtKeywordToken) : CompletionKeywordHandler<KaSession>(keyword) {
    init {
        check(keyword == KtTokens.BREAK_KEYWORD || keyword == KtTokens.CONTINUE_KEYWORD) {
            "Keyword should be either `break` or `continue`. But was: $keyword"
        }
    }

    context(_: KaSession)
    fun createLookups(expression: KtExpression?): Collection<LookupElement> {
        if (expression == null) return emptyList()
        val supportsNonLocalBreakContinue =
            expression.languageVersionSettings.supportsFeature(LanguageFeature.BreakContinueInInlineLambdas)
        return expression.parentsWithSelf
            .takeWhile { it !is KtDeclarationWithBody || canDoNonLocalJump(it, supportsNonLocalBreakContinue) }
            .filterIsInstance<KtLoopExpression>()
            .flatMapIndexed { index: Int, loop: KtLoopExpression ->
                listOfNotNull(
                    if (index == 0) createKeywordElement(keyword.value) else null,
                    (loop.parent as? KtLabeledExpression)?.getLabelNameAsName()?.let { label ->
                        createKeywordElement(keyword.value, tail = label.labelNameToTail())
                    }
                )
            }
            .toList()
    }
    context(_: KaSession)
    private fun canDoNonLocalJump(
        body: KtDeclarationWithBody,
        supportsNonLocalBreakContinue: Boolean,
    ) = supportsNonLocalBreakContinue &&
            body is KtFunctionLiteral &&
            isInlineFunctionCall(body.findLabelAndCall().second)

    context(_: KaSession)
    override fun createLookups(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ): Collection<LookupElement> = createLookups(expression)
}

@OptIn(KaContextParameterApi::class)
context(_: KaSession)
fun isInlineFunctionCall(call: KtCallExpression?): Boolean =
    (call?.calleeExpression as? KtReferenceExpression)?.mainReference
        ?.resolveToSymbol()
        ?.let { it as? KaNamedFunctionSymbol }
        ?.isInline == true
