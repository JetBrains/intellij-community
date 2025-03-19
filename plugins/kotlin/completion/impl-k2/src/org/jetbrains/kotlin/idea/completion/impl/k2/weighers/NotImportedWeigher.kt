/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.base.util.ImportableFqNameClassifier
import org.jetbrains.kotlin.psi.UserDataProperty

internal object NotImportedWeigher {
    const val WEIGHER_ID = "kotlin.notImported"

    private enum class Weight {
        DEFAULT,
        SIBLING_IMPORTED,
        NOT_IMPORTED,
        NOT_TO_BE_USED_IN_KOTLIN
    }

    context(KaSession)
fun addWeight(context: WeighingContext, element: LookupElement, symbol: KaSymbol, availableWithoutImport: Boolean) {
        if (availableWithoutImport) return
        val fqName = when (symbol) {
            is KaClassLikeSymbol -> symbol.classId?.asSingleFqName()
            is KaCallableSymbol -> symbol.callableId?.asSingleFqName()
            is KaPackageSymbol -> symbol.fqName
            else -> null
        } ?: return
        val weight = when (context.importableFqNameClassifier.classify(fqName, symbol is KaPackageSymbol)) {
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