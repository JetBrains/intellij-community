// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

class KotlinHighLevelFunctionParameterInfoHandler :
    KotlinHighLevelParameterInfoWithCallHandlerBase<KtValueArgumentList, KtValueArgument>(
        KtValueArgumentList::class
    ) {
    override fun getActualParameters(arguments: KtValueArgumentList): Array<KtValueArgument?> = arguments.arguments.toTypedArray()

    override fun getActualParametersRBraceType(): KtSingleValueToken = KtTokens.RPAR

    override fun getArgumentListAllowedParentClasses(): Set<Class<KtCallElement>> = setOf(KtCallElement::class.java)
}
