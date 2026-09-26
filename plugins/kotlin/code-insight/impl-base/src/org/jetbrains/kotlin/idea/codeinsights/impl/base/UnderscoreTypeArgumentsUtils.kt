// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeProjection

@ApiStatus.Internal
object UnderscoreTypeArgumentsUtils {
    fun isUnderscoreTypeArgument(element: KtTypeProjection): Boolean {
        val typeArgumentList = element.parent as? KtTypeArgumentList ?: return false
        if (typeArgumentList.parent !is KtCallExpression) return false
        val typeReference = element.typeReference ?: return false
        return typeReference.isPlaceholder
    }

    fun replaceTypeProjection(
        element: KtTypeProjection, argumentList: KtTypeArgumentList, renderedNewType: String
    ): KtTypeProjection {
        val text = argumentList.arguments.joinToString(", ", "<", ">") {
            if (it == element) {
                renderedNewType
            } else {
                it.text
            }
        }

        val indexOfReplacedType = argumentList.arguments.indexOf(element)
        val newArgumentList = KtPsiFactory(element.project).createTypeArguments(text)
        return newArgumentList.arguments[indexOfReplacedType]
    }
}