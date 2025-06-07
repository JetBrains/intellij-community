// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getOrCreateParameterList

val scopeFunctionsList: List<FqName> = listOf(
    "kotlin.also",
    "kotlin.let",
    "kotlin.takeIf",
    "kotlin.takeUnless",
).map { FqName(it) }

fun KtLambdaExpression.addExplicitItParameter(): KtParameter {
    return functionLiteral
        .getOrCreateParameterList()
        .addParameterBefore(
            KtPsiFactory(project).createLambdaParameterList(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier).parameters.first(),
            null,
        )
}
