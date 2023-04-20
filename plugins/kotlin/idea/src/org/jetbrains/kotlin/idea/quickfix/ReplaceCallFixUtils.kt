// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunctionDescriptor
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.types.typeUtil.makeNullable


fun KtExpression.shouldHaveNotNullType(): Boolean {
    val type = when (val parent = parent) {
        is KtBinaryExpression -> {
            val default = parent.left?.let { it.getType(it.analyze()) }
            val resolvedCall = parent.operationReference.resolveToCall()
            val function = resolvedCall?.resultingDescriptor as? FunctionDescriptor
            val isInfix = function?.isInfix ?: false
            if (isInfix) {
                val expression = this.let { it.getType(it.analyze()) }?.makeNullable()
                val valueParameters = function?.valueParameters
                val typeParameters = function?.typeParameters
                val isValidType = valueParameters?.any { param -> param.type.toString() == expression.toString() } ?: false
                        || typeParameters?.isNotEmpty() ?: false
                if (isValidType) {
                    return false
                } else {
                    default
                }
            } else {
                default
            }
        }
        is KtProperty -> parent.typeReference?.let { it.analyze()[BindingContext.TYPE, it] }
        is KtReturnExpression -> parent.getTargetFunctionDescriptor(analyze())?.returnType
        is KtValueArgument -> {
            val call = parent.getStrictParentOfType<KtCallExpression>()?.resolveToCall()
            (call?.getArgumentMapping(parent) as? ArgumentMatch)?.valueParameter?.type
        }
        is KtBlockExpression -> {
            if (parent.statements.lastOrNull() != this) return false
            val functionLiteral = parent.parent as? KtFunctionLiteral ?: return false
            if (functionLiteral.parent !is KtLambdaExpression) return false
            functionLiteral.analyze()[BindingContext.FUNCTION, functionLiteral]?.returnType
        }
        else -> null
    } ?: return false
    return !type.isMarkedNullable && !type.isUnit() && !type.isTypeParameter()
}
