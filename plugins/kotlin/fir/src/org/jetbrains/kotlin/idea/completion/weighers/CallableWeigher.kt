/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.completion.weighers

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementWeigher
import org.jetbrains.kotlin.idea.completion.contributors.callableWeight
import org.jetbrains.kotlin.idea.completion.contributors.helpers.CallableMetadataProvider

internal object CallableWeigher {
    const val WEIGHER_ID = "kotlin.callableWeigher"

    private enum class Weight1 {
        LOCAL,
        MEMBER_OR_EXTENSION,
        GLOBAL_OR_STATIC,
        TYPE_PARAMETER_EXTENSION,
        RECEIVER_CAST_REQUIRED
    }

    private enum class Weight2 {
        THIS_CLASS_MEMBER,
        BASE_CLASS_MEMBER,
        THIS_TYPE_EXTENSION,
        BASE_TYPE_EXTENSION,
        OTHER
    }

    private data class CompoundWeight(val weight1: Weight1, val receiverIndex: Int, val weight2: Weight2) : Comparable<CompoundWeight> {
        override fun compareTo(other: CompoundWeight): Int {
            return compareValuesBy(
                this,
                other,
                { it.weight1 },
                { it.receiverIndex },
                { it.weight2 },
            )
        }
    }

    object Weigher : LookupElementWeigher(WEIGHER_ID) {
        override fun weigh(element: LookupElement): Comparable<*>? {
            val weight = element.callableWeight ?: return null
            val w1 = when (weight.kind) {
                CallableMetadataProvider.CallableKind.Local -> Weight1.LOCAL

                CallableMetadataProvider.CallableKind.ThisClassMember,
                CallableMetadataProvider.CallableKind.BaseClassMember,
                CallableMetadataProvider.CallableKind.ThisTypeExtension,
                CallableMetadataProvider.CallableKind.BaseTypeExtension -> Weight1.MEMBER_OR_EXTENSION

                CallableMetadataProvider.CallableKind.GlobalOrStatic -> Weight1.GLOBAL_OR_STATIC

                CallableMetadataProvider.CallableKind.TypeParameterExtension -> Weight1.TYPE_PARAMETER_EXTENSION

                is CallableMetadataProvider.CallableKind.ReceiverCastRequired -> Weight1.RECEIVER_CAST_REQUIRED
            }
            val w2 = when (weight.kind) {
                CallableMetadataProvider.CallableKind.ThisClassMember -> Weight2.THIS_CLASS_MEMBER
                CallableMetadataProvider.CallableKind.BaseClassMember -> Weight2.BASE_CLASS_MEMBER
                CallableMetadataProvider.CallableKind.ThisTypeExtension -> Weight2.THIS_TYPE_EXTENSION
                CallableMetadataProvider.CallableKind.BaseTypeExtension -> Weight2.BASE_TYPE_EXTENSION
                else -> Weight2.OTHER
            }
            return CompoundWeight(w1, weight.receiverIndex ?: Int.MAX_VALUE, w2)
        }
    }
}