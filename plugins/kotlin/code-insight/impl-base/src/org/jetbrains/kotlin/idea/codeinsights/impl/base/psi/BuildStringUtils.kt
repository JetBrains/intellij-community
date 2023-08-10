// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.buildExpression

/**
 * Generates a `kotlin.text.buildString` call from [operandsWithExtras]. A separate `append(operand)` call is generated for each
 * [KtExpression] operand. The [PsiElement] extras associated with an operand are appended to the end of their operand's line as text.
 */
fun generateBuildStringCallWithExtras(
    project: Project,
    operandsWithExtras: List<Pair<KtExpression, List<PsiElement>>>,
): KtExpression =
    KtPsiFactory(project).buildExpression {
        appendFixedText("kotlin.text.buildString {\n")
        operandsWithExtras.forEach { (operand, extras) ->
            appendFixedText("append(")
            appendExpression(operand)
            appendFixedText(")")
            if (extras.isNotEmpty()) {
                appendFixedText(" ")
                extras.forEach { appendNonFormattedText(it.text) }
            }
            appendFixedText("\n")
        }
        appendFixedText("}")
    }

fun generateBuildStringCall(project: Project, operands: List<KtExpression>): KtExpression =
    generateBuildStringCallWithExtras(project, operands.map { Pair(it, emptyList()) })
