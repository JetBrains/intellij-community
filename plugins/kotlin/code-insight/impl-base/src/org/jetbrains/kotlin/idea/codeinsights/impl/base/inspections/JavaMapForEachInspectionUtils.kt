// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.inspections

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.refactoring.singleLambdaArgumentExpression
import org.jetbrains.kotlin.psi.KtCallExpression

@ApiStatus.Internal
object JavaMapForEachInspectionUtils {

    fun applicableByPsi(element: KtCallExpression): Boolean {
        val calleeExpression = element.calleeExpression ?: return false
        if (calleeExpression.text != "forEach") return false
        if (element.valueArguments.size != 1) return false

        val lambda = element.singleLambdaArgumentExpression() ?: return false
        val lambdaParameters = lambda.valueParameters
        return lambdaParameters.size == 2 && lambdaParameters.all { it.destructuringDeclaration == null }
    }
}