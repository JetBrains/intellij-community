// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors.keywords

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.completion.KeywordLookupObject
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.createKeywordElement
import org.jetbrains.kotlin.idea.completion.keywords.CompletionKeywordHandler
import org.jetbrains.kotlin.idea.completion.labelNameToTail
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiver
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KtTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KtAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.types.Variance

internal class ThisKeywordHandler(
    private val basicContext: FirBasicCompletionContext
) : CompletionKeywordHandler<KtAnalysisSession>(KtTokens.THIS_KEYWORD) {
    @OptIn(ExperimentalStdlibApi::class)
    override fun KtAnalysisSession.createLookups(
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
        val receivers = basicContext.originalKtFile.getScopeContextForPosition(expression).implicitReceivers

        receivers.forEachIndexed { index, receiver ->
            if (!canReferenceSymbolByThis(parameters, receiver.ownerSymbol)) {
                return@forEachIndexed
            }
            val labelName = if (index != 0) getThisLabelBySymbol(receiver.ownerSymbol) else null
            result += createThisLookupElement(receiver, labelName)
        }

        return result
    }

    private fun KtAnalysisSession.canReferenceSymbolByThis(parameters: CompletionParameters, symbol: KtSymbol): Boolean {
        if (symbol !is KtClassOrObjectSymbol) return true
        if (symbol.classKind != KtClassKind.COMPANION_OBJECT) return true
        val companionPsi = symbol.psi as KtClassOrObject
        return parameters.offset in companionPsi.textRange
    }

    private fun KtAnalysisSession.createThisLookupElement(receiver: KtImplicitReceiver, labelName: Name?): LookupElement {
        return createKeywordElement("this", labelName.labelNameToTail(), lookupObject = KeywordLookupObject())
            .withTypeText(receiver.type.render(KtTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT))
    }

    private fun KtAnalysisSession.getThisLabelBySymbol(symbol: KtSymbol): Name? = when {
        symbol is KtNamedSymbol && !symbol.name.isSpecial -> symbol.name
        symbol is KtAnonymousFunctionSymbol -> {
            val psi = symbol.psi as KtFunctionLiteral
            psi.findLabelAndCall().first
        }
        else -> null
    }
}