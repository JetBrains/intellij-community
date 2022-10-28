// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon.keywords

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.completion.createKeywordElement
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandler
import org.jetbrains.kotlin.idea.completion.labelNameToTail
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
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
class BreakContinueKeywordHandler(keyword: KtKeywordToken) : CompletionKeywordHandler<CompletionKeywordHandler.NO_CONTEXT>(keyword) {
    init {
        check(keyword == KtTokens.BREAK_KEYWORD || keyword == KtTokens.CONTINUE_KEYWORD) {
            "Keyword should be either `break` or `continue`. But was: $keyword"
        }
    }

    fun createLookups(expression: KtExpression?): Collection<LookupElement> =
        if (expression == null) emptyList()
        else buildList {
            for (parent in expression.parentsWithSelf) {
                when (parent) {
                    is KtLoopExpression -> {
                        if (isEmpty()) {
                            add(createKeywordElement(keyword.value))
                        }
                        val label = (parent.parent as? KtLabeledExpression)?.getLabelNameAsName()
                        if (label != null) {
                            add(createKeywordElement(keyword.value, tail = label.labelNameToTail()))
                        }
                    }

                    is KtDeclarationWithBody -> break //TODO: support non-local break's&continue's when they are supported by compiler
                }
            }
        }

    override fun NO_CONTEXT.createLookups(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ): Collection<LookupElement> = createLookups(expression)
}
