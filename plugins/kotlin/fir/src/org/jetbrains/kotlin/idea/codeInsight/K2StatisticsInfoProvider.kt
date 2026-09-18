// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.psi.statistics.StatisticsInfo
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.rendering.KaPiece
import org.jetbrains.kotlin.analysis.api.rendering.KaRenderer
import org.jetbrains.kotlin.analysis.api.rendering.pushEmpty
import org.jetbrains.kotlin.analysis.api.rendering.renderToString
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.idea.util.withQualifiedNames
import org.jetbrains.kotlin.idea.util.withValueParameterTypesOnly
import org.jetbrains.kotlin.idea.util.withoutAnnotations
import org.jetbrains.kotlin.idea.util.withoutDeclarationKeywords
import org.jetbrains.kotlin.idea.util.withoutModifiers

/**
 * Implementation in K1: [org.jetbrains.kotlin.idea.completion.KotlinStatisticsInfo]
 */
object K2StatisticsInfoProvider {
    /**
     * The renderer skips some features of a declaration to provide concise (but still unambiguous) description of the declaration.
     */
    private val renderer: KaRenderer = KaRenderer.withQualifiedNames.copy {
        withoutAnnotations()
        withoutModifiers()
        // The declaration keyword and the return type of a function carry no information the callable id does not already give.
        withoutDeclarationKeywords()
        withValueParameterTypesOnly()
        pushEmpty(KaPiece.FunctionReturnType)
        pushEmpty(KaPiece.NamedFunctionReturnType)
        pushEmpty(KaPiece.PropertyInitializer)
    }

    context(_: KaSession)
    fun forDeclarationSymbol(symbol: KaDeclarationSymbol, context: String = ""): StatisticsInfo = when (symbol) {
        is KaClassLikeSymbol -> symbol.classId?.asFqNameString()?.let { StatisticsInfo(context, it) }
        is KaCallableSymbol -> symbol.callableId?.let { callableId ->
            val containerFqName = callableId.classId?.asFqNameString() ?: callableId.packageName
            val declarationText = renderer.renderToString(symbol)
            StatisticsInfo(context, "$containerFqName###$declarationText")
        }

        else -> null
    } ?: StatisticsInfo.EMPTY
}
