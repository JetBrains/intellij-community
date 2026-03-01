// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.renderer.render

object ConvertLambdaToReferenceUtils {

    fun KtCallExpression.getCallReferencedName(): String? =
        (calleeExpression as? KtNameReferenceExpression)?.getSafeReferencedName()

    fun KtNameReferenceExpression.getSafeReferencedName(): String = getReferencedNameAsName().render()

    fun KtLambdaExpression.singleStatementOrNull(): KtExpression? = bodyExpression?.statements?.singleOrNull()

    fun KtLambdaExpression.isArgument(): Boolean {
        return this === getStrictParentOfType<KtValueArgument>()?.getArgumentExpression()?.let(KtPsiUtil::safeDeparenthesize)
    }
}