// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern

private val appendFunctionName = Name.identifier("append")
private val appendRangeFunctionName = Name.identifier("appendRange")

internal class ReplaceWithStringBuilderAppendRangeInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, ReplaceWithStringBuilderAppendRangeInspection.Context>(),
    CleanupLocalInspectionTool {

    data class Context(
        val firstArg: SmartPsiElementPointer<KtExpression>,
        val secondArg: SmartPsiElementPointer<KtExpression>,
        val thirdArg: SmartPsiElementPointer<KtExpression>,
        val foldSumValue: Int?,
        val needSum: Boolean,
        val needNotNullAssertOnFirstArg: Boolean
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        callExpressionVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun getProblemDescription(element: KtCallExpression, context: Context): @InspectionMessage String =
        KotlinBundle.message("replace.with.0", appendRangeFunctionName)

    override fun createQuickFix(element: KtCallExpression, context: Context): KotlinModCommandQuickFix<KtCallExpression> =
        ReplaceFix(context)

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        if (!element.platform.isJvm()) return null
        val calleeExpression = element.calleeExpression ?: return null
        if (calleeExpression.text != appendFunctionName.asString()) return null
        if (element.valueArguments.size != 3) return null

        val resolvedCall = element.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        val symbol = resolvedCall.symbol

        val parameters = symbol.valueParameters
        if (parameters.size != 3) return null

        val firstParamType = parameters[0].returnType
        val secondParamType = parameters[1].returnType
        val thirdParamType = parameters[2].returnType

        if (!isCharArrayType(firstParamType) || !secondParamType.isIntType || !thirdParamType.isIntType) {
            return null
        }

        val args = element.valueArguments
        val firstArg = args[0].getArgumentExpression() ?: return null
        val secondArg = args[1].getArgumentExpression() ?: return null
        val thirdArg = args[2].getArgumentExpression() ?: return null

        val secondArgAsInt = secondArg.toIntOrNull()
        val thirdArgAsInt = thirdArg.toIntOrNull()
        val foldSumValue = if (secondArgAsInt != null && thirdArgAsInt != null) secondArgAsInt + thirdArgAsInt else null
        val needSum = foldSumValue != null || (secondArgAsInt != 0)

        val needNotNullAssert = isNullable(firstArg)

        return Context(
            firstArg.createSmartPointer(),
            secondArg.createSmartPointer(),
            thirdArg.createSmartPointer(),
            foldSumValue,
            needSum,
            needNotNullAssert
        )
    }

    private fun KaSession.isCharArrayType(type: KaType): Boolean {
        return type.isArrayOrPrimitiveArray &&
                type.arrayElementType?.isCharType == true

    }

    private fun KaSession.isNullable(expression: KtExpression): Boolean {
        val type = expression.expressionType ?: return false

        // Check if we're inside a null-check context
        if (isInNullCheckContext(expression)) {
            return false // Smart cast to non-null
        }

        return type.isNullable
    }

    private fun isInNullCheckContext(expression: KtExpression): Boolean {
        // Walk up the tree to see if we're in an if-statement with a null check
        var parent = expression.parent
        while (parent != null) {
            if (parent is KtIfExpression) {
                val condition = parent.condition
                // Check if the condition contains a null check for our expression
                if (condition != null && containsNullCheck(condition, expression)) {
                    return true
                }
            }
            parent = parent.parent
        }
        return false
    }

    private fun containsNullCheck(condition: KtExpression, target: KtExpression): Boolean {
        // Simple heuristic: check if condition contains "!= null" for the target
        return condition.text.contains("${target.text} != null") ||
                condition.text.contains("null != ${target.text}")
    }

    private class ReplaceFix(private val context: Context) : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getName(): String = KotlinBundle.message("replace.with.0", appendRangeFunctionName)
        override fun getFamilyName(): String = name

        override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
            val writableCall = updater.getWritable(element)
            val firstArg = context.firstArg.element ?: return
            val secondArg = context.secondArg.element ?: return
            val thirdArg = context.thirdArg.element ?: return

            val psiFactory = KtPsiFactory(project)

            val callee = writableCall.calleeExpression ?: return
            callee.replace(psiFactory.createCalleeExpression(appendRangeFunctionName.toString()))

            val writableArgs = writableCall.valueArguments
            if (writableArgs.size != 3) return
            val writableFirstArg = writableArgs[0].getArgumentExpression() ?: return
            val writableThirdArg = writableArgs[2].getArgumentExpression() ?: return

            val newExpression = when {
                context.foldSumValue != null -> psiFactory.createExpression(context.foldSumValue.toString())
                context.needSum -> psiFactory.createExpressionByPattern("$0 + $1", secondArg, thirdArg)
                else -> null
            }

            newExpression?.let { writableThirdArg.replace(it) }

            if (context.needNotNullAssertOnFirstArg) {
                writableFirstArg.replace(psiFactory.createExpressionByPattern("$0!!", firstArg))
            }
        }

        private fun KtPsiFactory.createCalleeExpression(functionName: String): KtExpression =
            (createExpression("$functionName()") as KtCallExpression).calleeExpression!!
    }
}

private fun KtExpression.toIntOrNull(): Int? = (this as? KtConstantExpression)?.text?.toIntOrNull()