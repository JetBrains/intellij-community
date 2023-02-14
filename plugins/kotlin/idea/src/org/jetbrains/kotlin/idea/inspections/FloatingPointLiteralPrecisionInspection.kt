// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.dfa.getKotlinType
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.expressionVisitor
import org.jetbrains.kotlin.psi.stubs.ConstantValueKind
import org.jetbrains.kotlin.psi.stubs.elements.KtConstantExpressionElementType
import org.jetbrains.kotlin.types.typeUtil.isFloat
import java.math.BigDecimal

/**
 * Highlight floating point literals that exceed precision of the corresponding type.
 *
 * Some floating point values can't be represented using IEEE 754 floating point standard.
 * For example, the literal 9_999_999_999.000001 has the same representation as a Double
 * as the literal 9_999_999_999.000002. Specifying excess digits hides the fact that
 * computations use the rounded value instead of the exact constant.
 *
 * This inspection highlights floating point constants whose literal representation
 * requires more precision than the floating point type can provide.
 * It does not try to detect rounding errors or otherwise check computation results.
 */
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class FloatingPointLiteralPrecisionInspection : AbstractKotlinInspection() {
    private object Holder {
        val FLOAT_LITERAL: KtConstantExpressionElementType = KtConstantExpressionElementType.kindToConstantElementType(ConstantValueKind.FLOAT_CONSTANT)
        val FORMATTING_CHARACTERS_REGEX: Regex = Regex("[_fF]")
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return expressionVisitor {
            if ((it is KtConstantExpression) && (it.elementType == Holder.FLOAT_LITERAL)) {
                val isFloat = it.getKotlinType()?.isFloat() ?: false
                val uppercaseSuffix = isFloat && it.text?.endsWith('F') ?: false
                val literal = it.text?.replace(Holder.FORMATTING_CHARACTERS_REGEX, "") ?: return@expressionVisitor

                try {
                    val parseResult = if (isFloat)
                        literal.toFloat().toString()
                    else
                        literal.toDouble().toString()

                    val roundedValue = BigDecimal(parseResult)
                    val exactValue = BigDecimal(literal)
                    if (exactValue.compareTo(roundedValue) != 0) {
                        val replacementText = if (isFloat)
                            parseResult + if (uppercaseSuffix) "F" else "f"
                        else
                            parseResult

                        holder.registerProblem(
                            it,
                            KotlinBundle.message("floating.point.literal.precision.inspection"),
                            ProblemHighlightType.WEAK_WARNING,
                            FloatingPointLiteralPrecisionQuickFix(replacementText))
                    }
                } catch (e: NumberFormatException) {
                    return@expressionVisitor
                }
            }
        }
    }
}

private class FloatingPointLiteralPrecisionQuickFix(val replacementText: String) : LocalQuickFix {
    override fun getName(): String = KotlinBundle.message("replace.with.0", replacementText)

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? KtConstantExpression ?: return
        element.replace(KtPsiFactory(project).createExpression(replacementText))
    }
}
