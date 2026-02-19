// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class ConvertLongToDurationInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): String = KotlinBundle.message("long.to.duration.conversion")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val psiFactory = KtPsiFactory(project)
            val writableElement = updater.getWritable(element)

            val firstValueArgument = writableElement.valueArguments.firstOrNull() ?: return
            val firstArgumentExpr = firstValueArgument.getArgumentExpression() ?: return

            val withMilliseconds = psiFactory.createExpression("a.milliseconds") as KtDotQualifiedExpression
            withMilliseconds.receiverExpression.replace(firstArgumentExpr)

            firstArgumentExpr.replace(withMilliseconds)
        }
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Unit? {
        val function = element.resolveToCall()?.successfulFunctionCallOrNull()?.symbol ?: return null
        val callableId = function.callableId ?: return null
        if (callableId !in supportedCoroutineFunctions) return null
        if (!isLongFirstParameter(function)) return null
        return Unit
    }

    private fun KaSession.isLongFirstParameter(function: KaFunctionSymbol): Boolean {
        val firstParam = function.valueParameters.firstOrNull() ?: return false
        return firstParam.returnType.isLongType
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val calleeExpression = element.calleeExpression?.text ?: return false
        if (calleeExpression !in allTargetFunctionNames) return false
        
        // Skip calls with named arguments for now
        val firstArgument = element.valueArguments.firstOrNull() ?: return false
        if (firstArgument.getArgumentName() != null) return false
        
        return true
    }

    override fun getProblemDescription(element: KtCallExpression, context: Unit): String = 
        KotlinBundle.message("inspection.convert.long.to.duration.descriptor")

}

private val allTargetNames = mapOf(
    "delay" to "kotlinx.coroutines",
    "withTimeout" to "kotlinx.coroutines", 
    "withTimeoutOrNull" to "kotlinx.coroutines",
    "debounce" to "kotlinx.coroutines.flow",
    "sample" to "kotlinx.coroutines.flow",
    "throttle" to "kotlinx.coroutines.flow", 
    "timeout" to "kotlinx.coroutines.flow",
    "onTimeout" to "kotlinx.coroutines.selects",
    "advanceTimeBy" to "kotlinx.coroutines.test",
    "retryWhen" to "kotlinx.coroutines.flow"
)

private val supportedCoroutineFunctions = allTargetNames.map { (functionName, packageName) ->
    CallableId(FqName(packageName), Name.identifier(functionName)) }.toSet()

private val allTargetFunctionNames = allTargetNames.keys