// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.parameterInfo

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.codeInsight.ellipsis
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import java.awt.Color


@ApiStatus.Internal
object KotlinParameterInfoBase {
    @JvmField
    val GREEN_BACKGROUND: Color = JBColor(Color(231, 254, 234), Gray._100)

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