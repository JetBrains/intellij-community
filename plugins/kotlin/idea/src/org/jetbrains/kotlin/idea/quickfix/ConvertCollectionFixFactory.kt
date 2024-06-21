// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix.CollectionType
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

internal object ConvertCollectionFixFactory {
    private fun getConversionTypeOrNull(expressionType: KotlinType, expectedType: KotlinType): CollectionType? {
        val expressionCollectionType = expressionType.getCollectionType() ?: return null
        val expectedCollectionType = expectedType.getCollectionType() ?: return null
        if (expressionCollectionType == expectedCollectionType) return null

        val expressionTypeArg = expressionType.arguments.singleOrNull()?.type ?: return null
        val expectedTypeArg = expectedType.arguments.singleOrNull()?.type ?: return null
        if (!expressionTypeArg.isSubtypeOf(expectedTypeArg)) return null

        return expectedCollectionType.specializeFor(expressionCollectionType)
    }

    fun createIfAvailable(expression: KtExpression, expressionType: KotlinType, expectedType: KotlinType): IntentionAction? {
        val collectionType = getConversionTypeOrNull(expressionType, expectedType) ?: return null
        return ConvertCollectionFix(expression, collectionType).asIntention()
    }

    fun KotlinType.getCollectionType(acceptNullableTypes: Boolean = false): CollectionType? {
        if (isMarkedNullable && !acceptNullableTypes) return null
        return CollectionType.entries.firstOrNull { KotlinBuiltIns.isConstructedFromGivenClass(this, it.fqName) }
    }
}