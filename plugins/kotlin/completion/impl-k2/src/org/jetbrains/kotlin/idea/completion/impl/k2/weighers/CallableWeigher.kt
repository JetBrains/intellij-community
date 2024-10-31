/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CallableMetadataProvider
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CallableMetadataProvider.getCallableMetadata
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CompletionSymbolOrigin
import org.jetbrains.kotlin.idea.completion.lookups.factories.FunctionCallLookupObject
import org.jetbrains.kotlin.psi.UserDataProperty

internal object CallableWeigher {
    const val WEIGHER_ID = "kotlin.callableWeigher"

    private enum class Weight1 {
        LOCAL,
        MEMBER_OR_EXTENSION,
        TYPE_PARAMETER_EXTENSION,
        GLOBAL_OR_STATIC,
        RECEIVER_CAST_REQUIRED
    }

    private enum class Weight2 {
        THIS_CLASS_MEMBER,
        BASE_CLASS_MEMBER,
        THIS_TYPE_EXTENSION,
        BASE_TYPE_EXTENSION,
        OTHER
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<*>? {
            val weight = element.callableWeight ?: return null
            val w1 = when (weight.kind) {
                CallableMetadataProvider.CallableKind.LOCAL -> Weight1.LOCAL

                CallableMetadataProvider.CallableKind.THIS_CLASS_MEMBER,
                CallableMetadataProvider.CallableKind.BASE_CLASS_MEMBER,
                CallableMetadataProvider.CallableKind.THIS_TYPE_EXTENSION,
                CallableMetadataProvider.CallableKind.BASE_TYPE_EXTENSION -> Weight1.MEMBER_OR_EXTENSION

                CallableMetadataProvider.CallableKind.GLOBAL -> Weight1.GLOBAL_OR_STATIC

                CallableMetadataProvider.CallableKind.TYPE_PARAMETER_EXTENSION -> Weight1.TYPE_PARAMETER_EXTENSION

                CallableMetadataProvider.CallableKind.RECEIVER_CAST_REQUIRED -> Weight1.RECEIVER_CAST_REQUIRED
            }
            val w2 = when (weight.kind) {
                CallableMetadataProvider.CallableKind.THIS_CLASS_MEMBER -> Weight2.THIS_CLASS_MEMBER
                CallableMetadataProvider.CallableKind.BASE_CLASS_MEMBER -> Weight2.BASE_CLASS_MEMBER
                CallableMetadataProvider.CallableKind.THIS_TYPE_EXTENSION -> Weight2.THIS_TYPE_EXTENSION
                CallableMetadataProvider.CallableKind.BASE_TYPE_EXTENSION -> Weight2.BASE_TYPE_EXTENSION
                else -> Weight2.OTHER
            }

            return CompoundWeight3(w1, weight.scopeIndex ?: Int.MAX_VALUE, w2)
        }
    }

    context(KaSession)
    fun LookupElement.addCallableWeight(
        context: WeighingContext,
        signature: KaCallableSignature<*>,
        symbolOrigin: CompletionSymbolOrigin,
    ) {
        if (context.isPositionInsideImportOrPackageDirective) return

        val isFunctionalVariableCall = signature.symbol is KaVariableSymbol
                && `object` is FunctionCallLookupObject
        callableWeight = getCallableMetadata(context, signature, symbolOrigin, isFunctionalVariableCall)
    }

    internal var LookupElement.callableWeight: CallableMetadataProvider.CallableMetadata? by UserDataProperty(
        Key<CallableMetadataProvider.CallableMetadata>("KOTLIN_CALLABlE_WEIGHT")
    )
        private set
}