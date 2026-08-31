// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

@ApiStatus.Internal
context(session: KaSession)
fun KtExpression.isSideEffectFreeCondition(): Boolean =
  when (val expression = KtPsiUtil.deparenthesize(this)) {
    is KtConstantExpression -> expression.node.elementType == KtNodeTypes.BOOLEAN_CONSTANT
    is KtSimpleNameExpression -> expression.isSideEffectFreeReference()
    else -> false
  }

@OptIn(KaExperimentalApi::class)
context(session: KaSession)
private fun KtSimpleNameExpression.isSideEffectFreeReference(): Boolean =
  when (val symbol = resolveSuccessfulSymbol()) {
    is KaValueParameterSymbol -> true
    is KaLocalVariableSymbol -> (symbol.psi as? KtProperty)?.let {
      !it.hasDelegate() && !it.hasModifier(KtTokens.LATEINIT_KEYWORD)
    } ?: true
    else -> false
  }
