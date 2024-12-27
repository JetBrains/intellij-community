// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.parameterInfo

import org.jetbrains.kotlin.idea.codeInsight.ellipsis
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*


object KotlinParameterInfoBase {
    fun getDefaultValueStringRepresentation(defaultValue: KtExpression): String {
        var text = defaultValue.text

        if (defaultValue is KtNameReferenceExpression) {
            val resolve = defaultValue.reference?.resolve()
            if (resolve is KtProperty && resolve.hasModifier(KtTokens.CONST_KEYWORD)) {
                resolve.initializer?.text?.let { text = it }
            }
        }

        if (text.length <= 32) {
            return text
        }

        if (defaultValue is KtConstantExpression || defaultValue is KtStringTemplateExpression) {
            if (text.startsWith("\"")) {
                return "\"${text.substring(0, 30)}$ellipsis\""
            } else if (text.startsWith("\'")) {
                return "\'${text.substring(0, 30)}$ellipsis\'"
            }
        }

        return text.substring(0, 31) + ellipsis
    }
}