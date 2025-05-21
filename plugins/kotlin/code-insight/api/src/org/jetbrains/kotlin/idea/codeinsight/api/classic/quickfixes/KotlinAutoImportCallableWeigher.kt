// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Extends call expression weigher for K1 & K2, allowing other plugins to contribute into the weight calculation.
 * This weight is used to adjust the order of items in the auto-import list,
 * when we have a more specialized context (Composable functions, for example).
 */
interface KotlinAutoImportCallableWeigher {
    /**
     * Calculates extra weight for the imported symbol. This weight will be added to the final calculated result.
     * @param symbolToBeImported the declaration symbol which will be imported to fix the unresolved reference.
     * @param unresolvedReferenceExpression the expression where this import can be applied.
     * @return extra weight to add. Can be any number.
     */
    fun KaSession.weigh(
        symbolToBeImported: KaCallableSymbol,
        unresolvedReferenceExpression: KtNameReferenceExpression
    ): Int

    /**
     * Calculates extra weight for the imported symbol. This weight will be added to the final calculated result.
     * @param symbolToBeImported the declaration symbol which will be imported to fix the unresolved reference.
     * @param unresolvedReferenceExpression the expression where this import can be applied.
     * @return extra weight to add. Can be any number.
     */
    fun KaSession.weigh(
        symbolToBeImported: KaClassSymbol,
        unresolvedReferenceExpression: KtNameReferenceExpression
    ): Int = 0

    companion object {
        val EP_NAME: ExtensionPointName<KotlinAutoImportCallableWeigher> =
            ExtensionPointName.create("com.intellij.kotlin.autoImportCallableWeigher")

        fun KaSession.weigh(
            symbolToBeImported: KaCallableSymbol,
            unresolvedReferenceExpression: KtNameReferenceExpression
        ): Int {
            return EP_NAME.extensionList.sumOf { with(it) { weigh(symbolToBeImported, unresolvedReferenceExpression) } }
        }

        fun KaSession.weigh(
            symbolToBeImported: KaClassSymbol,
            unresolvedReferenceExpression: KtNameReferenceExpression
        ): Int {
            return EP_NAME.extensionList.sumOf { with(it) { weigh(symbolToBeImported, unresolvedReferenceExpression) } }
        }
    }
}
