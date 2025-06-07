// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.parameterInfo

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.codeInsight.ellipsis
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*


@ApiStatus.Internal
object KotlinParameterInfoBase {
    data class ExpressionValue(val text: String, val isConstValue: Boolean)

    fun getDefaultValueStringRepresentation(defaultValue: KtExpression): ExpressionValue {
        var text = defaultValue.text
        var isConstValue = false
        var shouldBeTrimmed = true

        if (defaultValue is KtNameReferenceExpression) {
            val resolve = defaultValue.reference?.resolve()
            if (resolve is KtProperty && resolve.hasModifier(KtTokens.CONST_KEYWORD)) {
                resolve.initializer?.text?.let {
                    text = it
                    isConstValue = true
                }
            } else {
                shouldBeTrimmed = false
            }
        }

        if (!shouldBeTrimmed || text.length <= 32) {
            return ExpressionValue(text, isConstValue)
        }

        if (defaultValue is KtConstantExpression || defaultValue is KtStringTemplateExpression) {
            if (text.startsWith("\"")) {
                return ExpressionValue("${text.substring(0, 30)}$ellipsis\"", false)
            } else if (text.startsWith("\'")) {
                return ExpressionValue("${text.substring(0, 30)}$ellipsis\'", false)
            }
        }

        return ExpressionValue(text.substring(0, 31) + ellipsis, isConstValue)
    }
}