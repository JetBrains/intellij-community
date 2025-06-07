// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import com.intellij.psi.PsiConstantEvaluationHelper
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.ConstantExpressionEvaluator
import org.jetbrains.kotlin.asJava.computeExpression

class KotlinLightConstantExpressionEvaluator : ConstantExpressionEvaluator {

    override fun computeConstantExpression(expression: PsiElement, throwExceptionOnOverflow: Boolean): Any? {
        return computeExpression(expression, throwExceptionOnOverflow, null)
    }

    override fun computeExpression(
        expression: PsiElement,
        throwExceptionOnOverflow: Boolean,
        auxEvaluator: PsiConstantEvaluationHelper.AuxEvaluator?
    ): Any? = computeExpression(expression)
}
