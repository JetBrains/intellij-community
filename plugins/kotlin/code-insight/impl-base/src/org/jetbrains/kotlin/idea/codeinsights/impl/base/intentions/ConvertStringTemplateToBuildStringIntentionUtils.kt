// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsights.impl.base.entryPrefixLength
import org.jetbrains.kotlin.idea.codeinsights.impl.base.findPrefixLengthForPlainTextConversion
import org.jetbrains.kotlin.idea.codeinsights.impl.base.psi.generateBuildStringCall
import org.jetbrains.kotlin.psi.*

fun convertStringTemplateToBuildStringCall(element: KtStringTemplateExpression): KtExpression {
    val operands = createOperands(element)
    val call = generateBuildStringCall(element.project, operands)
    return element.replaced(call)
}

private fun createOperands(element: KtStringTemplateExpression): List<KtExpression> {
    val operands: MutableList<KtExpression> = mutableListOf()
    val stringBuilder = StringBuilder()
    val psiFactory = KtPsiFactory(element.project)
    val oldPrefixLength = element.entryPrefixLength

    fun addStringOperand() {
        if (stringBuilder.isNotEmpty()) {
            val withOriginalPrefix = createStringTemplate(psiFactory, stringBuilder.toString(), oldPrefixLength)
            val minimalSafePrefixLength = findPrefixLengthForPlainTextConversion(withOriginalPrefix)
            when {
                minimalSafePrefixLength < oldPrefixLength -> {
                    val withSmallerPrefix = createStringTemplate(psiFactory, stringBuilder.toString(), minimalSafePrefixLength)
                    operands.add(withSmallerPrefix)
                }
                else -> operands.add(withOriginalPrefix)
            }
            stringBuilder.clear()
        }
    }

    element.entries.forEach { entry ->
        when(entry) {
            is KtStringTemplateEntryWithExpression -> entry.expression?.let {
                addStringOperand()
                operands.add(it)
            }
            else -> stringBuilder.append(entry.text)
        }
    }
    addStringOperand()

    return operands
}

private fun createStringTemplate(psiFactory: KtPsiFactory, content: String, prefixLength: Int): KtStringTemplateExpression {
    return when {
        prefixLength > 1 -> psiFactory.createMultiDollarStringTemplate(content, prefixLength)
        else -> psiFactory.createStringTemplate(content)
    }
}
