// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.psi.statistics.StatisticsInfo
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.renderer.base.KtKeywordsRenderer
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KtRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.renderers.callables.KtCallableSignatureRenderer
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.utils.printer.PrettyPrinter
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.lexer.KtKeywordToken

/**
 * Implementation in K1: [org.jetbrains.kotlin.idea.completion.KotlinStatisticsInfo]
 */
object K2StatisticsInfoProvider {
    /**
     * The renderer skips some features of a declaration to provide concise (but still unambiguous) description of the declaration.
     */
    private val renderer = KtDeclarationRendererForSource.WITH_QUALIFIED_NAMES.with {
        annotationRenderer = annotationRenderer.with { annotationFilter = KtRendererAnnotationsFilter.NONE }
        modifiersRenderer = modifiersRenderer.with { keywordsRenderer = KtKeywordsRenderer.NONE }

        returnTypeFilter = object : KtCallableReturnTypeFilter {
            context(KtAnalysisSession)
            override fun shouldRenderReturnType(type: KtType, symbol: KtCallableSymbol): Boolean = symbol !is KtFunctionLikeSymbol
        }

        callableSignatureRenderer = object : KtCallableSignatureRenderer {
            context(KtAnalysisSession, KtDeclarationRenderer)
            override fun renderCallableSignature(symbol: KtCallableSymbol, keyword: KtKeywordToken?, printer: PrettyPrinter) =
                when (symbol) {
                    is KtValueParameterSymbol -> returnTypeRenderer.renderReturnType(symbol, printer)
                    else -> KtCallableSignatureRenderer.FOR_SOURCE.renderCallableSignature(symbol, keyword = null, printer)
                }
        }
    }

    context(KtAnalysisSession)
    fun forDeclarationSymbol(symbol: KtDeclarationSymbol, context: String = ""): StatisticsInfo = when (symbol) {
        is KtClassLikeSymbol -> symbol.classIdIfNonLocal?.asFqNameString()?.let { StatisticsInfo(context, it) }
        is KtCallableSymbol -> symbol.callableIdIfNonLocal?.let { callableId ->
            val containerFqName = callableId.classId?.asFqNameString() ?: callableId.packageName
            StatisticsInfo(context, "$containerFqName###${prettyPrint { renderer.renderDeclaration(symbol, this) }}")
        }

        else -> null
    } ?: StatisticsInfo.EMPTY
}
