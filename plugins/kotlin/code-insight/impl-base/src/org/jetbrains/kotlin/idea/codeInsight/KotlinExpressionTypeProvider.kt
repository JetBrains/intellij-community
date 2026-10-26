// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.lang.ExpressionTypeProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.codeinsight.utils.shouldShowType
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

abstract class KotlinExpressionTypeProvider : ExpressionTypeProvider<KtExpression>() {
    override fun getExpressionsAt(elementAt: PsiElement): List<KtExpression> {
        return elementAt.parentsWithSelf.filterIsInstance<KtExpression>()
            .filter { it.shouldShowType() && !it.hasValueAround(elementAt) }
            .toList()
    }
}

/** Inside its value, a declaration row repeats the type of the value rows. Its text also starts with the comments above it. */
private fun KtExpression.hasValueAround(element: PsiElement): Boolean {
    val values: List<KtElement> = when (this) {
        is KtProperty -> listOfNotNull(initializer, delegate) + accessors
        is KtFunction -> listOfNotNull(bodyExpression)
        else -> return false
    }
    return values.any { PsiTreeUtil.isAncestor(it, element, false) }
}
