// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.analysis.api.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.ShortenOption
import org.jetbrains.kotlin.analysis.api.components.ShortenOption.Companion.defaultCallableShortenOption
import org.jetbrains.kotlin.analysis.api.components.ShortenOption.Companion.defaultClassShortenOption
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * Shorten references in the given [element]. See [shortenReferencesInRange] for more details.
 */
fun shortenReferences(
    element: KtElement,
    classShortenOption: (KtClassLikeSymbol) -> ShortenOption = defaultClassShortenOption,
    callableShortenOption: (KtCallableSymbol) -> ShortenOption = defaultCallableShortenOption
): Unit = shortenReferencesInRange(
    element.containingKtFile,
    element.textRange,
    classShortenOption,
    callableShortenOption
)

/**
 * Shorten references in the given [file] and [range]. The function must be invoked on EDT thread because it modifies the underlying
 * PSI. This method analyze Kotlin code and hence could block the EDT thread for longer period of time. Hence, this method should be
 * called only to shorten references in *newly generated code* by IDE actions. In other cases, please consider using
 * [org.jetbrains.kotlin.analysis.api.components.KtReferenceShortenerMixIn] in a background thread to perform the analysis and then
 * modify PSI on the EDt thread by invoking [org.jetbrains.kotlin.analysis.api.components.ShortenCommand.invokeShortening]. */
@OptIn(KtAllowAnalysisOnEdt::class)
fun shortenReferencesInRange(
    file: KtFile,
    range: TextRange = file.textRange,
    classShortenOption: (KtClassLikeSymbol) -> ShortenOption = defaultClassShortenOption,
    callableShortenOption: (KtCallableSymbol) -> ShortenOption = defaultCallableShortenOption
) {
     val shortenCommand = allowAnalysisOnEdt {
        analyze(file) {
            collectPossibleReferenceShortenings(file, range, classShortenOption, callableShortenOption)
        }
    }
    shortenCommand.invokeShortening()
}
