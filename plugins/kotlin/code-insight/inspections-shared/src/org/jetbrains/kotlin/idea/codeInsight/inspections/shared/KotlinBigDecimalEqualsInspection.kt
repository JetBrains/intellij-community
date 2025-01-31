// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal class KotlinBigDecimalEqualsInspection : KotlinApplicableInspectionBase.Simple<KtExpression, KotlinBigDecimalEqualsInspection.Context>(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        expressionVisitor {
            visitTargetElement(it, holder, isOnTheFly)
        }

    override fun getProblemDescription(
        element: KtExpression,
        context: Context
    ): @InspectionMessage String =
        KotlinBundle.message("big.decimal.equals.problem.descriptor")

    override fun createQuickFix(
        element: KtExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtExpression> =
        object : KotlinModCommandQuickFix<KtExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String =
                KotlinBundle.message("big.decimal.equals")

            override fun applyFix(
                project: Project,
                element: KtExpression,
                updater: ModPsiUpdater
            ) {
                val psiFactory = KtPsiFactory(project)

                when (element) {
                    is KtBinaryExpression -> {
                        val expressionString = expressionString(context, element.left!!, element.right!!)
                        val compareToExpression = psiFactory.createExpression(expressionString)
                        element.replace(compareToExpression)
                    }
                    is KtCallExpression -> {
                        val qualifiedExpression = element.parent as KtQualifiedExpression
                        val receiverExpression = qualifiedExpression.receiverExpression
                        val argumentExpression = element.valueArguments.single().getArgumentExpression()!!

                        val expressionString = expressionString(context, receiverExpression, argumentExpression)
                        val compareToExpression = psiFactory.createExpression("($expressionString)")
                        qualifiedExpression.replace(compareToExpression)
                    }
                    else -> return
                }
            }

            private fun expressionString(
                context: Context,
                left: KtExpression,
                right: KtExpression
            ): String = buildString {
                // `equals` has a different contract than `compareTo`:
                // `compareTo` works with only non-nullable values
                when {
                    !context.nullableLeft && !context.nullableRight -> {
                        append(left.text)
                        append(".compareTo(")
                        append(right.text)
                        append(") ")
                        append(if (context.equals) "==" else "!=")
                        append(" 0")
                    }

                    context.nullableLeft && context.nullableRight -> {
                        append(left.text)
                        append("?.let { ")
                        append(right.text)
                        append("?.compareTo(it) ")
                        append(if (context.equals) "==" else "!=")
                        append(" 0 } ?: (")
                        append(right.text)
                        append(" == null)")
                    }

                    else -> {
                        // either left or right is null
                        val e1 = if (context.nullableLeft) left.text else right.text
                        val e2 = if (context.nullableLeft) right.text else left.text

                        append(e1)
                        append("?.compareTo(")
                        append(e2)
                        append(") ")
                        append(if (context.equals) "==" else "!=")
                        append(" 0")
                    }
                }
            }
        }

    override fun isApplicableByPsi(element: KtExpression): Boolean =
        // BigDecimal(1.0) == BigDecimal(1) or BigDecimal(1.0) != BigDecimal(1)
        element is KtBinaryExpression && (element.operationToken == KtTokens.EQEQ || element.operationToken == KtTokens.EXCLEQ) ||
                // or BigDecimal(1.0).equals(BigDecimal(1))
                element is KtCallExpression &&
                element.valueArguments.size == 1 &&
                element.calleeExpression?.text == "equals" &&
                element.parent is KtQualifiedExpression

    context(KaSession)
    override fun prepareContext(element: KtExpression): Context? {
        return when (element) {
            is KtBinaryExpression -> {
                val left = element.left ?: return null
                val right = element.right ?: return null

                val leftType = left.expressionType?.takeIf { isBigDecimal(it) } ?: return null
                val rightType = right.expressionType?.takeIf { isBigDecimal(it) } ?: return null

                val operatorCallableId =
                    element.operationReference.resolveToCall()?.successfulFunctionCallOrNull()?.symbol?.callableId ?: return null
                // to avoid FP with a custom operator extension function
                if (operatorCallableId != bigDecimalEquals) return null

                val nullableLeft = leftType.isMarkedNullable
                val nullableRight = rightType.isMarkedNullable

                Context(nullableLeft, nullableRight, element.operationToken == KtTokens.EQEQ)
            }
            is KtCallExpression -> {
                val expression = element.parent as? KtQualifiedExpression ?: return null
                val nullableReceiverType = expression.receiverExpression.expressionType?.isMarkedNullable == true
                val calleeExpression = element.calleeExpression
                val call =
                    calleeExpression?.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
                if (call.symbol.callableId != bigDecimalEquals) return null

                val singleArgument = element.valueArguments.singleOrNull() ?: return null
                val argumentExpression = singleArgument.getArgumentExpression() ?: return null
                val argumentType = argumentExpression.expressionType?.takeIf { isBigDecimal(it) } ?: return null

                Context(nullableReceiverType, argumentType.isMarkedNullable, true)
            }
            else -> null
        }
    }

    private fun KaSession.isBigDecimal(type: KaType): Boolean =
        (type.upperBoundIfFlexible() as? KaClassType)?.classId == bigDecimal

    internal class Context(
        val nullableLeft: Boolean,
        val nullableRight: Boolean,
        val equals: Boolean
    )

    companion object {
        private val bigDecimal: ClassId = ClassId.fromString("java/math/BigDecimal")
        private val bigDecimalEquals: CallableId = CallableId(bigDecimal, Name.identifier("equals"))
    }
}