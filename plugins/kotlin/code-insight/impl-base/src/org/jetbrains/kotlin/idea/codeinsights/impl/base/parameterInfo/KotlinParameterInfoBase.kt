// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.parameterInfo

import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression


object KotlinParameterInfoBase {
    fun getDefaultValueStringRepresentation(defaultValue: KtExpression): String {
        val text = defaultValue.text
        if (text.length <= 32) {
            return text
        }

        if (defaultValue is KtConstantExpression || defaultValue is KtStringTemplateExpression) {
            if (text.startsWith("\"")) {
                return "\"...\""
            } else if (text.startsWith("\'")) {
                return "\'...\'"
            }
        }
        return "..."
    }
}