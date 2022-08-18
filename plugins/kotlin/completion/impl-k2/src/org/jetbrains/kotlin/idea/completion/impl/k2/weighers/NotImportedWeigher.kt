/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.idea.completion.ImportableFqNameClassifier
import org.jetbrains.kotlin.idea.completion.contributors.availableWithoutImport
import org.jetbrains.kotlin.psi.UserDataProperty

internal object NotImportedWeigher {
    const val WEIGHER_ID = "kotlin.notImported"

    private enum class Weight {
        DEFAULT,
        SIBLING_IMPORTED,
        NOT_IMPORTED,
        NOT_TO_BE_USED_IN_KOTLIN
    }

    fun KtAnalysisSession.addWeight(context: WeighingContext, element: LookupElement, symbol: KtSymbol) {
        if (element.availableWithoutImport) return
        val fqName = when (symbol) {
            is KtClassLikeSymbol -> symbol.classIdIfNonLocal?.asSingleFqName()
            is KtCallableSymbol -> symbol.callableIdIfNonLocal?.asSingleFqName()
            is KtPackageSymbol -> symbol.fqName
            else -> null
        } ?: return
        val weight = when (context.importableFqNameClassifier.classify(fqName, symbol is KtPackageSymbol)) {
            ImportableFqNameClassifier.Classification.siblingImported -> Weight.SIBLING_IMPORTED
            ImportableFqNameClassifier.Classification.notImported -> Weight.NOT_IMPORTED
            ImportableFqNameClassifier.Classification.notToBeUsedInKotlin -> Weight.NOT_TO_BE_USED_IN_KOTLIN
            else -> null
        }
        if (weight != null) element.notImportedWeight = weight
    }

    private var LookupElement.notImportedWeight by UserDataProperty(Key<Weight>("KOTLIN_NOT_IMPORTED_WEIGHT"))

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<Nothing> = element.notImportedWeight ?: Weight.DEFAULT
    }
}