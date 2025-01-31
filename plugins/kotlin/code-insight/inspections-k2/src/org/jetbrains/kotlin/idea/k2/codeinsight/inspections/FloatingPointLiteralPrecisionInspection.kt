// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.stubs.ConstantValueKind
import org.jetbrains.kotlin.psi.stubs.elements.KtConstantExpressionElementType
import java.math.BigDecimal

/**
 * Highlight floating point literals that exceed the precision of the corresponding type.
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
internal class FloatingPointLiteralPrecisionInspection : KotlinApplicableInspectionBase.Simple<KtConstantExpression, String>() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {
        override fun visitConstantExpression(expression: KtConstantExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(
        element: KtConstantExpression,
        context: String
    ): @InspectionMessage String {
        return KotlinBundle.message("floating.point.literal.precision.inspection")
    }

    override fun createQuickFix(
        element: KtConstantExpression,
        context: String
    ): KotlinModCommandQuickFix<KtConstantExpression> {
        return FloatingPointLiteralPrecisionQuickFix(context)
    }

    context(KaSession)
    override fun prepareContext(element: KtConstantExpression): String? {
        if (element.elementType == KtConstantExpressionElementType.kindToConstantElementType(ConstantValueKind.FLOAT_CONSTANT)) {
            val isFloat = element.expressionType?.isFloatType == true
            val uppercaseSuffix = isFloat && element.text?.endsWith('F') == true
            val literal = element.text?.replace(Regex("[_fF]"), "") ?: return null

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
                    return replacementText
                }
            } catch (_: NumberFormatException) {
                return null
            }
        }
        return null
    }
}

private class FloatingPointLiteralPrecisionQuickFix(val replacementText: String) : KotlinModCommandQuickFix<KtConstantExpression>() {
    override fun getName(): String = KotlinBundle.message("replace.with.0", replacementText)

    override fun getFamilyName(): String = name

    override fun applyFix(
            project: Project,
            element: KtConstantExpression,
            updater: ModPsiUpdater,
    ) {
        element.replace(KtPsiFactory(project).createExpression(replacementText))
    }
}