// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.lang.ExpressionTypeProvider
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeinsight.utils.shouldShowType
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

abstract class KotlinExpressionTypeProvider : ExpressionTypeProvider<KtExpression>() {
    override fun getExpressionsAt(elementAt: PsiElement): List<KtExpression> {
        return elementAt.parentsWithSelf.filterIsInstance<KtExpression>().filter { it.shouldShowType() }.toList()
    }
}
