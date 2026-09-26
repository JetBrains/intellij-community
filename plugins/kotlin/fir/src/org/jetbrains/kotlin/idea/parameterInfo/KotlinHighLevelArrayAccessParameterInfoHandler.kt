// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.parameterInfo

import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtContainerNode
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.allChildren

class KotlinHighLevelArrayAccessParameterInfoHandler :
    KotlinHighLevelParameterInfoWithCallHandlerBase<KtContainerNode, KtExpression>(KtContainerNode::class) {

    override fun getArgumentListAllowedParentClasses(): Set<Class<KtArrayAccessExpression>> = setOf(KtArrayAccessExpression::class.java)

    override fun getActualParameters(containerNode: KtContainerNode): Array<out KtExpression> =
        containerNode.allChildren.filterIsInstance<KtExpression>().toList().toTypedArray()

    override fun getActualParametersRBraceType(): KtSingleValueToken = KtTokens.RBRACKET
}