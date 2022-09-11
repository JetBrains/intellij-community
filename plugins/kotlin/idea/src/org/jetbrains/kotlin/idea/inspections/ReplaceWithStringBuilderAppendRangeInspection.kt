// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.idea.util.getDataFlowAwareTypes
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.isInt
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ReplaceWithStringBuilderAppendRangeInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    companion object {
        private const val appendFunctionName = "append"
        private const val appendRangeFunctionName = "appendRange"
        private val appendFunctionFqName = FqName("java.lang.StringBuilder.append")
        private val charArrayFqName = FqName("kotlin.CharArray")
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        callExpressionVisitor(fun(callExpression: KtCallExpression) {
            if (!callExpression.platform.isJvm()) return
            val calleeExpression = callExpression.calleeExpression ?: return
            if (calleeExpression.text != appendFunctionName) return
            if (callExpression.valueArguments.size != 3) return

            val descriptor = callExpression.resolveToCall()?.resultingDescriptor ?: return
            if (descriptor.fqNameSafe != appendFunctionFqName) return

            val params = descriptor.valueParameters
            if (params.size != 3 || !params[0].isCharArray() || !params[1].isInt() || !params[2].isInt()) return

            holder.registerProblem(
                calleeExpression,
                KotlinBundle.message("replace.with.0", appendRangeFunctionName),
                ReplaceFix(appendRangeFunctionName)
            )
        })

    private fun ValueParameterDescriptor.isCharArray(): Boolean = type.fqName == charArrayFqName

    private fun ValueParameterDescriptor.isInt(): Boolean = type.isInt()

    private class ReplaceFix(private val functionName: String) : LocalQuickFix {
        override fun getName(): String = KotlinBundle.message("replace.with.0", functionName)

        override fun getFamilyName(): String = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val calleeExpression = descriptor.psiElement as? KtExpression ?: return
            val callExpression = calleeExpression.parent as? KtCallExpression ?: return

            val args = callExpression.valueArguments
            val firstArg = args.getOrNull(0)?.getArgumentExpression() ?: return
            val secondArg = args.getOrNull(1)?.getArgumentExpression() ?: return
            val thirdArg = args.getOrNull(2)?.getArgumentExpression() ?: return

            val psiFactory = KtPsiFactory(callExpression)
            calleeExpression.replace(psiFactory.createCalleeExpression(functionName))

            val secondArgAsInt = secondArg.toIntOrNull()
            val thirdArgAsInt = thirdArg.toIntOrNull()
            if (secondArgAsInt != null && thirdArgAsInt != null) {
                thirdArg.replace(psiFactory.createExpression(secondArgAsInt.plus(thirdArgAsInt).toString()))
            } else if (secondArgAsInt != 0) {
                thirdArg.replace(psiFactory.createExpressionByPattern("$0 + $1", secondArg, thirdArg))
            }

            if (firstArg.isNullable()) {
                firstArg.replace(psiFactory.createExpressionByPattern("$0!!", firstArg))
            }
        }

        private fun KtPsiFactory.createCalleeExpression(functionName: String): KtExpression =
            (createExpression("$functionName()") as KtCallExpression).calleeExpression!!

        private fun KtExpression.toIntOrNull(): Int? = safeAs<KtConstantExpression>()?.text?.toIntOrNull()

        private fun KtExpression.isNullable(): Boolean {
            return getDataFlowAwareTypes(this).any { it.isNullable() }
        }
    }
}
