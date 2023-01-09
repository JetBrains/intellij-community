// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.idea.completion.KeywordCompletion
import org.jetbrains.kotlin.idea.completion.context.*
import org.jetbrains.kotlin.idea.completion.contributors.keywords.OverrideKeywordHandler
import org.jetbrains.kotlin.idea.completion.contributors.keywords.ReturnKeywordHandler
import org.jetbrains.kotlin.idea.completion.contributors.keywords.SuperKeywordHandler
import org.jetbrains.kotlin.idea.completion.contributors.keywords.ThisKeywordHandler
import org.jetbrains.kotlin.idea.completion.implCommon.keywords.BreakContinueKeywordHandler
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandlerProvider
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandlers
import org.jetbrains.kotlin.idea.completion.keywords.DefaultCompletionKeywordHandlerProvider
import org.jetbrains.kotlin.idea.completion.keywords.createLookups
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionWithLabel
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

internal class FirKeywordCompletionContributor(basicContext: FirBasicCompletionContext, priority: Int) :
    FirCompletionContributorBase<FirRawPositionCompletionContext>(basicContext, priority) {
    private val keywordCompletion = KeywordCompletion(object : KeywordCompletion.LanguageVersionSettingProvider {
        override fun getLanguageVersionSetting(element: PsiElement) = element.languageVersionSettings
        override fun getLanguageVersionSetting(module: Module) = module.languageVersionSettings
    })

    private val resolveDependentCompletionKeywordHandlers = ResolveDependentCompletionKeywordHandlerProvider(basicContext)

    override fun KtAnalysisSession.complete(positionContext: FirRawPositionCompletionContext) {
        val expression = when (positionContext) {
            is FirNameReferencePositionContext -> positionContext.reference.expression.let {
                it.parentsWithSelf.match(KtLabelReferenceExpression::class, KtContainerNode::class, last = KtExpressionWithLabel::class)
                    ?: it
            }
            is FirTypeConstraintNameInWhereClausePositionContext, is FirIncorrectPositionContext, is FirClassifierNamePositionContext ->
                error("keyword completion should not be called for ${positionContext::class.simpleName}")
            is FirValueParameterPositionContext,
            is FirMemberDeclarationExpectedPositionContext,
            is FirUnknownPositionContext -> null
        }
        completeWithResolve(expression ?: positionContext.position, expression)
    }


    private fun KtAnalysisSession.completeWithResolve(position: PsiElement, expression: KtExpression?) {
        complete(position) { lookupElement, keyword ->
            val lookups = DefaultCompletionKeywordHandlerProvider.getHandlerForKeyword(keyword)
                ?.createLookups(parameters, expression, lookupElement, project)
                ?: resolveDependentCompletionKeywordHandlers.getHandlerForKeyword(keyword)?.run {
                    createLookups(parameters, expression, lookupElement, project)
                }
                ?: listOf(lookupElement)
            sink.addAllElements(lookups)
        }
    }

    private inline fun complete(position: PsiElement, crossinline complete: (LookupElement, String) -> Unit) {
        keywordCompletion.complete(position, prefixMatcher, targetPlatform.isJvm()) { lookupElement ->
            val keyword = lookupElement.lookupString
            complete(lookupElement, keyword)
        }
    }
}

private class ResolveDependentCompletionKeywordHandlerProvider(
    basicContext: FirBasicCompletionContext
) : CompletionKeywordHandlerProvider<KtAnalysisSession>() {
    override val handlers = CompletionKeywordHandlers(
        ReturnKeywordHandler,
        BreakContinueKeywordHandler(KtTokens.CONTINUE_KEYWORD),
        BreakContinueKeywordHandler(KtTokens.BREAK_KEYWORD),
        OverrideKeywordHandler(basicContext),
        ThisKeywordHandler(basicContext),
        SuperKeywordHandler,
    )
}