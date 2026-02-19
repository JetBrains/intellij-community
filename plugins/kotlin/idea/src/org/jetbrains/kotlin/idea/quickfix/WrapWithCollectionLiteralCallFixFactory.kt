// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf

internal object WrapWithCollectionLiteralCallFixFactory {
    fun create(expectedType: KotlinType, expressionType: KotlinType, element: KtExpression): List<IntentionAction> {
        if (element.getStrictParentOfType<KtAnnotationEntry>() != null) return emptyList()

        val collectionType =
            with(ConvertCollectionFixFactory) {
                expectedType.getCollectionType(acceptNullableTypes = true)
            } ?: return emptyList()

        val expectedArgumentType =
            expectedType
                .arguments.singleOrNull()
                ?.takeIf { it.projectionKind != Variance.IN_VARIANCE }
                ?.type
                ?: return emptyList()

        val result = mutableListOf<WrapWithCollectionLiteralCallFix>()

        val isNullExpression = element.isNullExpression()
        val literalFunctionName = collectionType.literalFunctionName
        if ((expressionType.isSubtypeOf(expectedArgumentType) || isNullExpression) && literalFunctionName != null) {
            result += WrapWithCollectionLiteralCallFix(element, literalFunctionName, wrapInitialElement = true)
        }

        // Replace "null" with emptyList()
        val emptyCollectionFunction = collectionType.emptyCollectionFunction
        if (isNullExpression && emptyCollectionFunction != null) {
            result += WrapWithCollectionLiteralCallFix(element, emptyCollectionFunction, wrapInitialElement = false)
        }

        return result.map { it.asIntention() }
    }
}