// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils

import org.jetbrains.kotlin.analysis.api.symbols.KaAnonymousFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Returns the label that can be used to refer to this declaration symbol.
 * In named functions it is the name of the function.
 * For an anonymous lambda argument it is the name of the function it is passed to.
 */
fun KaDeclarationSymbol.getThisLabelName(): String? {
    // For named symbols, return the name
    if (this is KaNamedSymbol) {
        val name = this.name
        if (!name.isSpecial) return name.asString()
    }
    
    // For anonymous functions, try to get the name of the function it's passed to
    if (this is KaAnonymousFunctionSymbol) {
        val function = psi as? KtFunction
        val argument = function?.parent as? KtValueArgument
            ?: (function?.parent as? KtLambdaExpression)?.parent as? KtValueArgument
        val callElement = argument?.getStrictParentOfType<KtCallElement>()
        val callee = callElement?.calleeExpression as? KtSimpleNameExpression
        if (callee != null) return callee.text
    }
    
    return null
}

/**
 * Returns the `this` expression that can be used to refer to the given declaration symbol.
 */
fun KaDeclarationSymbol.getThisWithLabel(): String {
    val labelName = getThisLabelName()
    return if (labelName == null || labelName.isEmpty()) "this" else "this@$labelName"
}