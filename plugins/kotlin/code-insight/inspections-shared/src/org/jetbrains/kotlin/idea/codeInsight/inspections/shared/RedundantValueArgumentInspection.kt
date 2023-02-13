// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode.CONSTANT_EXPRESSION_EVALUATION
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.sourcePsiSafe
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddArgumentNamesUtils
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class RedundantValueArgumentInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = valueArgumentVisitor(fun(argument: KtValueArgument) {
        val argumentExpression = argument.getArgumentExpression() ?: return
        val argumentList = argument.getStrictParentOfType<KtValueArgumentList>() ?: return
        val callElement = argumentList.getStrictParentOfType<KtCallElement>() ?: return

        val arguments = argumentList.arguments
        val argumentIndex = arguments.indexOf(argument).takeIf { it >= 0 } ?: return

        analyze(argument, action = fun KtAnalysisSession.() {
            val argumentConstantValue = argumentExpression.evaluate(CONSTANT_EXPRESSION_EVALUATION) ?: return

            val call = callElement.resolveCall().successfulFunctionCallOrNull() ?: return
            val parameterSymbol = call.argumentMapping[argumentExpression]?.symbol?.let { parameterSymbol ->
                val overriddenSymbols = call.partiallyAppliedSymbol.symbol.getAllOverriddenSymbols()
                val baseFunctionSymbol = overriddenSymbols.firstNotNullOfOrNull { overriddenSymbol ->
                    overriddenSymbol.asSignature().symbol.safeAs<KtFunctionSymbol>()?.takeIf { !it.isOverride }
                }
                baseFunctionSymbol?.valueParameters?.firstOrNull { it.name == parameterSymbol.name } ?: parameterSymbol
            } ?: return
            if (parameterSymbol.hasDefaultValue) {
                val parameter = (parameterSymbol).sourcePsiSafe<KtParameter>() ?: return
                if (parameter.isVarArg) {
                    return
                }

                val followingArgumentMapping = LinkedHashMap<Int, Name>()

                val followingArguments = arguments.withIndex().filter { it.index > argumentIndex }
                for ((followingArgumentIndex, followingArgument) in followingArguments) {
                    if (!followingArgument.isNamed()) {
                        val followingArgumentExpression = followingArgument.getArgumentExpression() ?: return
                        val followingParameterSymbol = call.argumentMapping[followingArgumentExpression]?.symbol ?: return
                        if (followingParameterSymbol.isVararg) {
                            return
                        }

                        followingArgumentMapping[followingArgumentIndex] = followingParameterSymbol.name
                    }
                }

                val defaultValueExpression = parameter.defaultValue ?: return
                val defaultConstantValue = defaultValueExpression.evaluate(CONSTANT_EXPRESSION_EVALUATION) ?: return

                if (argumentConstantValue.value == defaultConstantValue.value) {
                    val description = KotlinBundle.message("inspection.redundant.value.argument.annotation", parameterSymbol.name)
                    val quickFix = RemoveArgumentFix(followingArgumentMapping)
                    holder.registerProblem(argument, description, ProblemHighlightType.LIKE_UNUSED_SYMBOL, quickFix)
                }
            }
        })
    })

    private class RemoveArgumentFix(@SafeFieldForPreview private val followingArgumentMapping: Map<Int, Name>) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("fix.remove.argument.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val argument = descriptor.psiElement as? KtValueArgument ?: return
            val argumentList = argument.getStrictParentOfType<KtValueArgumentList>() ?: return

            for ((followingArgumentIndex, name) in followingArgumentMapping) {
                val followingArgument = argumentList.arguments[followingArgumentIndex]
                AddArgumentNamesUtils.addArgumentName(followingArgument, name)
            }

            argumentList.removeArgument(argument)
        }
    }
}