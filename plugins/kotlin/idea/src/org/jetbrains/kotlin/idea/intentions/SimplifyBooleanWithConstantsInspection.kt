// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isTrueConstant
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi2ir.deparenthesize
import org.jetbrains.kotlin.resolve.CompileTimeConstantUtils
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.isFlexible

internal class SimplifyBooleanWithConstantsInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, Unit>() {
    override fun getProblemDescription(element: KtBinaryExpression, context: Unit): @InspectionMessage String {
        return KotlinBundle.message("inspection.simplify.boolean.with.constants.display.name")
    }
    
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitorVoid = binaryExpressionVisitor { expression -> 
        visitTargetElement(expression, holder, isOnTheFly) 
    }

    context(KaSession)
    override fun prepareContext(element: KtBinaryExpression): Unit? {
        return areThereExpressionsToBeSimplified(element.topBinary()).asUnit
    }

    private fun KtBinaryExpression.topBinary(): KtBinaryExpression =
      this.parentsWithSelf.takeWhile { it is KtBinaryExpression }.lastOrNull() as? KtBinaryExpression ?: this

    private fun areThereExpressionsToBeSimplified(element: KtExpression?): Boolean {
        if (element == null) return false
        when (element) {
            is KtParenthesizedExpression -> return areThereExpressionsToBeSimplified(element.expression)

            is KtBinaryExpression -> {
                val op = element.operationToken
                if (op == KtTokens.ANDAND || op == KtTokens.OROR || op == KtTokens.EQEQ || op == KtTokens.EXCLEQ) {
                    if (areThereExpressionsToBeSimplified(element.left) && element.right.hasBooleanType()) return true
                    if (areThereExpressionsToBeSimplified(element.right) && element.left.hasBooleanType()) return true
                }
                if (isPositiveNegativeZeroComparison(element)) return false

            }
        }

        return element.canBeReducedToBooleanConstant()
    }

    private fun isPositiveNegativeZeroComparison(element: KtBinaryExpression): Boolean {
        val op = element.operationToken
        if (op != KtTokens.EQEQ && op != KtTokens.EQEQEQ) {
            return false
        }

        val left = element.left?.deparenthesize() as? KtExpression ?: return false
        val right = element.right?.deparenthesize() as? KtExpression ?: return false

        val context = element.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)

        fun KtExpression.getConstantValue() =
            ConstantExpressionEvaluator.getConstant(this, context)?.toConstantValue(TypeUtils.NO_EXPECTED_TYPE)?.value

        val leftValue = left.getConstantValue()
        val rightValue = right.getConstantValue()

        fun isPositiveZero(value: Any?) = value == +0.0 || value == +0.0f
        fun isNegativeZero(value: Any?) = value == -0.0 || value == -0.0f

        val hasPositiveZero = isPositiveZero(leftValue) || isPositiveZero(rightValue)
        val hasNegativeZero = isNegativeZero(leftValue) || isNegativeZero(rightValue)

        return hasPositiveZero && hasNegativeZero
    }

    override fun createQuickFix(
        element: KtBinaryExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtBinaryExpression> = object : KotlinModCommandQuickFix<KtBinaryExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("simplify.boolean.expression")

        override fun applyFix(project: Project, element: KtBinaryExpression, updater: ModPsiUpdater) {
            val topBinary = element.topBinary()
            val simplified = toSimplifiedExpression(topBinary)
            val result = topBinary.replaced(KtPsiUtil.safeDeparenthesize(simplified, true))
            removeRedundantAssertion(result)
        }
    }

    private fun removeRedundantAssertion(expression: KtExpression) {
        val callExpression = expression.getNonStrictParentOfType<KtCallExpression>() ?: return
        val fqName = callExpression.getCallableDescriptor()?.fqNameOrNull()
        val valueArguments = callExpression.valueArguments
        val isRedundant = fqName?.asString() == "kotlin.assert" &&
                valueArguments.singleOrNull()?.getArgumentExpression().isTrueConstant()
        if (isRedundant) callExpression.delete()
    }

    private fun toSimplifiedExpression(expression: KtExpression): KtExpression {
        val psiFactory = KtPsiFactory(expression.project)

        when {
            expression.canBeReducedToTrue() -> {
                return psiFactory.createExpression("true")
            }

            expression.canBeReducedToFalse() -> {
                return psiFactory.createExpression("false")
            }

            expression is KtParenthesizedExpression -> {
                val expr = expression.expression
                if (expr != null) {
                    val simplified = toSimplifiedExpression(expr)
                    return if (simplified is KtBinaryExpression) {
                        // wrap in new parentheses to keep the user's original format
                        psiFactory.createExpressionByPattern("($0)", simplified)
                    } else {
                        // if we now have a simpleName, constant, or parenthesized we don't need parentheses
                        simplified
                    }
                }
            }

            expression is KtBinaryExpression -> {
                if (!areThereExpressionsToBeSimplified(expression)) return expression.copied()
                val left = expression.left
                val right = expression.right
                val op = expression.operationToken
                if (left != null && right != null && (op == KtTokens.ANDAND || op == KtTokens.OROR || op == KtTokens.EQEQ || op == KtTokens.EXCLEQ)) {
                    val simpleLeft = simplifyExpression(left)
                    val simpleRight = simplifyExpression(right)
                    return when {
                        simpleLeft.canBeReducedToTrue() -> toSimplifiedBooleanBinaryExpressionWithConstantOperand(true, simpleRight, op)

                        simpleLeft.canBeReducedToFalse() -> toSimplifiedBooleanBinaryExpressionWithConstantOperand(false, simpleRight, op)

                        simpleRight.canBeReducedToTrue() -> toSimplifiedBooleanBinaryExpressionWithConstantOperand(true, simpleLeft, op)

                        simpleRight.canBeReducedToFalse() -> toSimplifiedBooleanBinaryExpressionWithConstantOperand(false, simpleLeft, op)

                        else -> {
                            val opText = expression.operationReference.text
                            psiFactory.createExpressionByPattern("$0 $opText $1", simpleLeft, simpleRight)
                        }
                    }
                }
            }
        }

        return expression.copied()
    }

    private fun toSimplifiedBooleanBinaryExpressionWithConstantOperand(
        constantOperand: Boolean,
        otherOperand: KtExpression,
        operation: IElementType
    ): KtExpression {
        val psiFactory = org.jetbrains.kotlin.psi.KtPsiFactory(otherOperand.project)
        when (operation) {
            KtTokens.OROR -> {
                if (constantOperand) return psiFactory.createExpression("true")
            }
            KtTokens.ANDAND -> {
                if (!constantOperand) return psiFactory.createExpression("false")
            }
            KtTokens.EQEQ, KtTokens.EXCLEQ -> toSimplifiedExpression(otherOperand).let {
                return if (constantOperand == (operation == KtTokens.EQEQ)) it
                else psiFactory.createExpressionByPattern("!$0", it)
            }
        }

        return toSimplifiedExpression(otherOperand)
    }

    private fun simplifyExpression(expression: KtExpression) = expression.replaced(toSimplifiedExpression(expression))

    private fun KtExpression?.hasBooleanType(): Boolean {
        val type = this?.getType(safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)) ?: return false
        return KotlinBuiltIns.isBoolean(type) && !type.isFlexible()
    }

    private fun KtExpression.canBeReducedToBooleanConstant(constant: Boolean? = null): Boolean =
        CompileTimeConstantUtils.canBeReducedToBooleanConstant(this, safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL), constant)

    private fun KtExpression.canBeReducedToTrue() = canBeReducedToBooleanConstant(true)

    private fun KtExpression.canBeReducedToFalse() = canBeReducedToBooleanConstant(false)
}