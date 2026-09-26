/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.impl.k2.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPackageSymbol
import org.jetbrains.kotlin.idea.base.util.ImportableFqNameClassifier
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.helpers.KtSymbolWithOrigin
import org.jetbrains.kotlin.psi.UserDataProperty

internal object NotImportedWeigher: KotlinLookupElementWeigher("kotlin.notImported"), KotlinSectionContextWeigher {

    private enum class Weight {
        DEFAULT,
        SIBLING_IMPORTED,
        NOT_IMPORTED,
        NOT_TO_BE_USED_IN_KOTLIN
    }

    context(_: KaSession, sectionContext: K2CompletionSectionContext<*>)
    override fun addWeight(lookupElement: LookupElement, symbolWithOrigin: KtSymbolWithOrigin<*>?) {
        val context = sectionContext.weighingContext
        val symbol = symbolWithOrigin?.symbol ?: return
        if (symbolWithOrigin.scopeKind != null) return

        val classification = when (symbol) {
            is KaClassLikeSymbol -> symbol.classId?.let { classId ->
                context.importableFqNameClassifier.classify(classId.asSingleFqName(), isPackage = false, classId.packageFqName)
            }
            is KaCallableSymbol -> symbol.callableId?.let {
                context.importableFqNameClassifier.classify(it.asSingleFqName(), isPackage = false)
            }
            is KaPackageSymbol -> context.importableFqNameClassifier.classify(symbol.fqName, isPackage = true)
            else -> null
        } ?: return

        val weight = when (classification) {
            ImportableFqNameClassifier.Classification.siblingImported -> Weight.SIBLING_IMPORTED
            ImportableFqNameClassifier.Classification.notImported -> Weight.NOT_IMPORTED
            ImportableFqNameClassifier.Classification.notToBeUsedInKotlin -> Weight.NOT_TO_BE_USED_IN_KOTLIN
            else -> null
        }
        if (weight != null) lookupElement.notImportedWeight = weight
    }

    private var LookupElement.notImportedWeight by UserDataProperty(Key<Weight>("KOTLIN_NOT_IMPORTED_WEIGHT"))

    override fun weigh(element: LookupElement): Comparable<Nothing> = element.notImportedWeight ?: Weight.DEFAULT
}