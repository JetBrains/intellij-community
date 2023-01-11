// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode.CONSTANT_EXPRESSION_EVALUATION
import org.jetbrains.kotlin.analysis.api.symbols.sourcePsiSafe
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class RedundantOptionalArgumentInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = valueArgumentVisitor(fun(argument: KtValueArgument) {
        val argumentExpression = argument.getArgumentExpression() ?: return

        val argumentList = argument.getStrictParentOfType<KtValueArgumentList>() ?: return
        val callElement = argumentList.getStrictParentOfType<KtCallElement>() ?: return

        if (hasUnnamedFollowingArguments(argumentList, argument)) {
            return
        }

        analyze(argument, action = fun KtAnalysisSession.() {
            val argumentConstantValue = argumentExpression.evaluate(CONSTANT_EXPRESSION_EVALUATION) ?: return

            val call = callElement.resolveCall().successfulFunctionCallOrNull() ?: return
            val parameterSymbol = call.argumentMapping[argumentExpression]?.symbol ?: return
            if (parameterSymbol.hasDefaultValue) {
                val parameter = parameterSymbol.sourcePsiSafe<KtParameter>()?.takeIf { !it.isVarArg } ?: return
                val parameterName = parameter.name ?: return

                val defaultValueExpression = parameter.defaultValue ?: return
                val defaultConstantValue = defaultValueExpression.evaluate(CONSTANT_EXPRESSION_EVALUATION) ?: return

                if (argumentConstantValue.value == defaultConstantValue.value) {
                    val description = KotlinBundle.message("inspection.redundant.optional.argument.annotation", parameterName)
                    holder.registerProblem(argument, description, ProblemHighlightType.LIKE_UNUSED_SYMBOL, RemoveArgumentFix())
                }
            }
        })
    })

    private fun hasUnnamedFollowingArguments(argumentList: KtValueArgumentList, argument: KtValueArgument): Boolean {
        val arguments = argumentList.arguments

        val argumentIndex = arguments.indexOf(argument)
        if (argumentIndex < 0 || argumentIndex == arguments.lastIndex) {
            return false
        }

        return arguments.subList(argumentIndex + 1, arguments.size).any { !it.isNamed() }
    }

    private class RemoveArgumentFix : LocalQuickFix {
        override fun getName() = KotlinBundle.message("fix.remove.argument.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val argument = descriptor.psiElement as? KtValueArgument ?: return
            val argumentList = argument.getStrictParentOfType<KtValueArgumentList>() ?: return
            argumentList.removeArgument(argument)
        }
    }
}
