// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.getOrCreateFunctionLiteralParameterList
import org.jetbrains.kotlin.idea.base.psi.insertParameterBefore
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory

@ApiStatus.Internal
val scopeFunctionsList: Array<FqName> = arrayOf(
    StandardKotlinNames.also,
    StandardKotlinNames.let,
    StandardKotlinNames.takeIf,
    StandardKotlinNames.takeUnless,
)

fun KtLambdaExpression.addExplicitItParameter(): KtParameter {
    return functionLiteral
        .getOrCreateFunctionLiteralParameterList()
        .insertParameterBefore(
            KtPsiFactory(project).createLambdaParameterList(StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier).parameters.first(),
            null,
        )
}
