// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.allOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.sourcePsiSafe
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.NamedArgumentUtils
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class RedundantValueArgumentInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = valueArgumentVisitor(fun(argument: KtValueArgument) {
        val argumentExpression = argument.getArgumentExpression() ?: return
        val argumentList = argument.getStrictParentOfType<KtValueArgumentList>() ?: return
        val callElement = argumentList.getStrictParentOfType<KtCallElement>() ?: return

        val arguments = argumentList.arguments
        val argumentIndex = arguments.indexOf(argument).takeIf { it >= 0 } ?: return

        analyze(argument) {
            val argumentConstantValue = argumentExpression.evaluate() ?: return
            val call = callElement.resolveToCall()?.successfulFunctionCallOrNull() ?: return
            val parameterSymbol = findTargetParameter(argumentExpression, call) ?: return

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
                val defaultConstantValue = defaultValueExpression.evaluate() ?: return

                if (argumentConstantValue.value == defaultConstantValue.value) {
                    val description = KotlinBundle.message("inspection.redundant.value.argument.annotation", parameterSymbol.name)
                    val quickFix = RemoveArgumentFix(followingArgumentMapping)
                    holder.registerProblem(argument, description, ProblemHighlightType.LIKE_UNUSED_SYMBOL, quickFix)
                }
            }
        }
    })

    context(_: KaSession)
    private fun findTargetParameter(argumentExpression: KtExpression, call: KaFunctionCall<*>): KaValueParameterSymbol? {
        val targetParameterSymbol = call.argumentMapping[argumentExpression]?.symbol ?: return null

        val targetFunctionSymbol = call.partiallyAppliedSymbol.symbol
        if (targetFunctionSymbol is KaNamedFunctionSymbol && targetFunctionSymbol.isOverride) {
            for (baseFunctionSymbol in targetFunctionSymbol.allOverriddenSymbols) {
                if (baseFunctionSymbol is KaNamedFunctionSymbol && !baseFunctionSymbol.isOverride) {
                    return baseFunctionSymbol.valueParameters.singleOrNull { it.name == targetParameterSymbol.name }
                }
            }

            return null
        }

        return targetParameterSymbol
    }

    private class RemoveArgumentFix(private val followingArgumentMapping: Map<Int, Name>) : PsiUpdateModCommandQuickFix() {

        override fun getFamilyName(): @IntentionName String = KotlinBundle.message("fix.remove.argument.text")

        override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
            val argument = element as? KtValueArgument ?: return
            val argumentList = argument.getStrictParentOfType<KtValueArgumentList>() ?: return

            for ((followingArgumentIndex, name) in followingArgumentMapping) {
                val followingArgument = argumentList.arguments[followingArgumentIndex]
                NamedArgumentUtils.addArgumentName(followingArgument, name)
            }

            argumentList.removeArgument(argument)
        }
    }
}