// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.copyConcatenatedStringToClipboard

import com.intellij.psi.impl.LanguageConstantExpressionEvaluator
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getTopmostParentOfType

internal class ConcatenatedStringGenerator {
    fun create(element: KtBinaryExpression): String {
        val binaryExpression = element.getTopmostParentOfType<KtBinaryExpression>() ?: element
        val stringBuilder = StringBuilder()
        binaryExpression.appendTo(stringBuilder)
        return stringBuilder.toString()
    }

    private fun KtBinaryExpression.appendTo(sb: StringBuilder) {
        left?.appendTo(sb)
        right?.appendTo(sb)
    }

    private fun KtExpression.appendTo(sb: StringBuilder) {
        when (this) {
            is KtBinaryExpression -> this.appendTo(sb)
            is KtConstantExpression -> sb.append(text)
            is KtStringTemplateExpression -> this.appendTo(sb)
            else -> sb.append(convertToValueIfCompileTimeConstant() ?: "?")
        }
    }

    private fun KtStringTemplateExpression.appendTo(sb: StringBuilder) {
        collectDescendantsOfType<KtStringTemplateEntry>().forEach { stringTemplate ->
            when (stringTemplate) {
                is KtLiteralStringTemplateEntry -> sb.append(stringTemplate.text)
                is KtEscapeStringTemplateEntry -> sb.append(stringTemplate.unescapedValue)
                else -> sb.append(stringTemplate.expression?.convertToValueIfCompileTimeConstant() ?: "?")
            }
        }
    }

    private fun KtExpression.convertToValueIfCompileTimeConstant(): String? {
        val expressionEvaluator = LanguageConstantExpressionEvaluator.INSTANCE.forLanguage(language)
        val value = expressionEvaluator.computeConstantExpression(
            /* expression = */ this,
            /* throwExceptionOnOverflow = */ false,
        ) ?: return null

        return value.toString()
    }
}
