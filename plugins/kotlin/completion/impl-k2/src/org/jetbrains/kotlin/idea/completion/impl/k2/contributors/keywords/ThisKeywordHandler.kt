// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.keywords

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaImplicitReceiver
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.idea.completion.KeywordLookupObject
import org.jetbrains.kotlin.idea.completion.createKeywordElement
import org.jetbrains.kotlin.idea.completion.impl.k2.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandler
import org.jetbrains.kotlin.idea.completion.labelNameToTail
import org.jetbrains.kotlin.idea.completion.lookups.CompletionShortNamesRenderer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.types.Variance

internal class ThisKeywordHandler(
    private val basicContext: FirBasicCompletionContext
) : CompletionKeywordHandler<KaSession>(KtTokens.THIS_KEYWORD) {
    context(KaSession)
    override fun createLookups(
        parameters: CompletionParameters,
        expression: KtExpression?,
        lookup: LookupElement,
        project: Project
    ): Collection<LookupElement> {
        if (expression == null) {
            // for completion in secondary constructor delegation call
            return listOf(lookup)
        }

        val result = mutableListOf<LookupElement>()
        val receivers = basicContext.originalKtFile.scopeContext(expression).implicitReceivers

        receivers.forEachIndexed { index, receiver ->
            if (!canReferenceSymbolByThis(parameters, receiver.ownerSymbol)) {
                return@forEachIndexed
            }
            // only add label when `receiver` can't be called with `this` without label
            val labelName = if (index != 0 || basicContext.prefixMatcher.prefix.startsWith(KtTokens.THIS_KEYWORD.value + "@")) {
                getThisLabelBySymbol(receiver.ownerSymbol)
            } else null

            result += createThisLookupElement(receiver, labelName)
        }

        return result
    }

    context(KaSession)
    private fun canReferenceSymbolByThis(parameters: CompletionParameters, symbol: KaSymbol): Boolean {
        if (symbol !is KaClassSymbol) return true
        if (symbol.classKind != KaClassKind.COMPANION_OBJECT) return true
        val companionPsi = symbol.psi as KtClassOrObject
        return parameters.offset in companionPsi.textRange
    }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createThisLookupElement(receiver: KaImplicitReceiver, labelName: Name?): LookupElement {
        return createKeywordElement(KtTokens.THIS_KEYWORD.value, labelName.labelNameToTail(), lookupObject = KeywordLookupObject())
            .withTypeText(receiver.type.render(CompletionShortNamesRenderer.rendererVerbose, position = Variance.INVARIANT))
    }

    context(KaSession)
    private fun getThisLabelBySymbol(symbol: KaSymbol): Name? = when {
        symbol is KaNamedSymbol && !symbol.name.isSpecial -> symbol.name
        symbol is KaAnonymousFunctionSymbol -> {
            val psi = symbol.psi as KtFunctionLiteral
            psi.findLabelAndCall().first
        }

        else -> null
    }
}