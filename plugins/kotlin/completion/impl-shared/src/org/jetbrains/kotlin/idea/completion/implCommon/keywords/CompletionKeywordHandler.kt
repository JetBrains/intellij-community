// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.keywords

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.psi.KtExpression

abstract class CompletionKeywordHandler<CONTEXT>(
    val keyword: KtKeywordToken
) {
    object NO_CONTEXT

    context(CONTEXT)
    abstract fun createLookups(
        parameters: CompletionParameters, // todo replace with KotlinFirCompletionParameters eventually
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ): Collection<LookupElement>
}

inline fun <CONTEXT> completionKeywordHandler(
    keyword: KtKeywordToken,
    crossinline create: context(CONTEXT)(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ) -> Collection<LookupElement>
) = object : CompletionKeywordHandler<CONTEXT>(keyword) {
    context(CONTEXT)
    override fun createLookups(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ): Collection<LookupElement> = create(this@CONTEXT, parameters, expression, lookup, project)
}

/**
 * Create a list of [LookupElement] for [CompletionKeywordHandler] which has no context
 *
 * This function is needed to avoid writing `with(CompletionKeywordHandler.NO_CONTEXT) { ... }` to create such lookups
 */
fun CompletionKeywordHandler<CompletionKeywordHandler.NO_CONTEXT>.createLookups(
    parameters: CompletionParameters,
    expression: KtExpression?,
    lookup: LookupElement,
    project: Project
): Collection<LookupElement> = with(CompletionKeywordHandler.NO_CONTEXT) { createLookups(parameters, expression, lookup, project) }
