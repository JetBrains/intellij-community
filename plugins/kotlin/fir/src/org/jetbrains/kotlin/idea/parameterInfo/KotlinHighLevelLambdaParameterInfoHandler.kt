// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtLambdaArgument


class KotlinHighLevelLambdaParameterInfoHandler :
    KotlinHighLevelParameterInfoWithCallHandlerBase<KtLambdaArgument, KtLambdaArgument>(KtLambdaArgument::class) {

    override fun getActualParameters(lambdaArgument: KtLambdaArgument): Array<KtLambdaArgument> = arrayOf(lambdaArgument)

    override fun getActualParametersRBraceType(): KtSingleValueToken = KtTokens.RBRACE

    override fun getArgumentListAllowedParentClasses(): Set<Class<KtLambdaArgument>> = setOf(KtLambdaArgument::class.java)

    override fun getCurrentArgumentIndex(offset: Int, argumentList: KtLambdaArgument): Int {
        val size = (argumentList.parent as? KtCallElement)?.valueArguments?.size ?: 1
        return size - 1
    }
}