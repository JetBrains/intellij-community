// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

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
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.types.typeUtil.isInt

class ReplaceWithStringBuilderAppendRangeInspection: AbstractKotlinInspection() {
    companion object {
        private val appendFunctionFqName = FqName("java.lang.StringBuilder.append")
        private val appendFunctionName = appendFunctionFqName.shortName().asString()
        private const val appendRangeFunctionName = "appendRange"
        private val charArrayFqName = FqName("kotlin.CharArray")
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = callExpressionVisitor(fun(callExpression: KtCallExpression) {
        if (!callExpression.platform.isJvm()) return

        val calleeExpression = callExpression.calleeExpression ?: return
        if (calleeExpression.text != appendFunctionName || callExpression.valueArguments.size != 3) return

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

    private fun ValueParameterDescriptor.isCharArray() = type.fqName == charArrayFqName

    private fun ValueParameterDescriptor.isInt() = type.isInt()

    private class ReplaceFix(private val functionName: String) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("replace.with.0", functionName)

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val calleeExpression = descriptor.psiElement as? KtExpression ?: return
            val callExpression = calleeExpression.parent as? KtCallExpression ?: return

            val args = callExpression.valueArguments
            val secondArg = args.getOrNull(1)?.getArgumentExpression() ?: return
            val thirdArg = args.getOrNull(2)?.getArgumentExpression() ?: return

            val psiFactory = KtPsiFactory(callExpression)
            calleeExpression.replace(psiFactory.createCalleeExpression(functionName))
            if (secondArg is KtConstantExpression && thirdArg is KtConstantExpression) {
                thirdArg.replace(psiFactory.createExpression(secondArg.text.toInt().plus(thirdArg.text.toInt()).toString()))
            } else {
                thirdArg.replace(psiFactory.createExpressionByPattern("$0 + $1", secondArg, thirdArg))
            }
        }

        private fun KtPsiFactory.createCalleeExpression(functionName: String): KtExpression =
            (createExpression("$functionName()") as KtCallExpression).calleeExpression!!
    }
}
