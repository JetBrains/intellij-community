// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.parents
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.equalsOrEqualsByPsi
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.resources.BUNDLE
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.RunBlockingInSuspendFunctionInspection.Context
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.getCallExpressionSymbol
import org.jetbrains.kotlin.idea.codeinsight.utils.isInlinedArgument
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.refactoring.singleLambdaArgumentExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.psi.psiUtil.hasSuspendModifier

private val COROUTINE_CONTEXT_FQ_NAME = FqName("kotlin.coroutines.CoroutineContext")
private val COROUTINE_SCOPE_FQ_NAME = FqName("kotlinx.coroutines.CoroutineScope")
private val RUN_BLOCKING_FQ_NAME = FqName("kotlinx.coroutines.runBlocking")
private val WITH_CONTEXT_FQ_NAME = FqName("kotlinx.coroutines.withContext")
private const val RUN_BLOCKING_FUNCTION_NAME = "runBlocking"
private const val RUN_FUNCTION_NAME = "run"

internal class RunBlockingInSuspendFunctionInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Context>() {

    enum class FixType(@NonNls @PropertyKey(resourceBundle = BUNDLE) val key: String) {
        INLINE("fix.replace.run.blocking.with.inline"),
        RUN("fix.replace.run.blocking.with.run"),
        WITH_CONTEXT("fix.replace.run.blocking.with.withContext"),
    }

    data class Context(
        val labelReferenceExpressions: List<SmartPsiElementPointer<KtLabelReferenceExpression>>,
        val fixType: FixType,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> = ApplicabilityRanges.calleeExpression(element)

    override fun getProblemDescription(element: KtCallExpression, context: Context): @InspectionMessage String =
        KotlinBundle.message("inspection.run.blocking.in.suspend.function.description")

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        return element.getCallNameExpression()?.text == RUN_BLOCKING_FUNCTION_NAME
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        // Check if we're in a suspend context (either suspend function or suspend lambda)
        if (!isInSuspendContext(element)) return null
        
        val fixType = when (element.valueArguments.size) {
            1 -> {
                val lambdaArgumentExpression = element.singleLambdaArgumentExpression() ?: return null
                val statements = lambdaArgumentExpression.bodyExpression?.statements ?: return null
                when (statements.size) {
                    1 -> FixType.INLINE
                    else -> FixType.RUN
                }
            }

            2 -> FixType.WITH_CONTEXT
            else -> return null
        }

        val function = element.resolveToFunctionSymbol(this@KaSession) ?: return null

        if (!isRunBlocking(function)) return null
        val lambdaArgument = element.lambdaArguments.singleOrNull() ?: return null
        val functionLiteral = lambdaArgument.getLambdaExpression()?.functionLiteral ?: return null
        val anonymousFunctionSymbol = functionLiteral.symbol

        val labelReferenceExpressions = functionLiteral.descendantsOfType<KtLabelReferenceExpression>().filter {
            it.resolveExpression().equalsOrEqualsByPsi(anonymousFunctionSymbol) 
        }.map { it.createSmartPointer() }.toList()

        return Context(
            labelReferenceExpressions,
            fixType,
        )
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getName(): String = KotlinBundle.message(context.fixType.key)

        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("fix.replace.run.family")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val callNameExpression = element.getCallNameExpression() ?: return

            val writableLabelReferenceExpressions = context.labelReferenceExpressions.mapNotNull {
                updater.getWritable(it.element)
            }

            val replacement = when (context.fixType) {
                FixType.WITH_CONTEXT -> {
                    element.containingKtFile.addImport(WITH_CONTEXT_FQ_NAME)
                    WITH_CONTEXT_FQ_NAME.shortName().asString()
                }

                FixType.RUN -> RUN_FUNCTION_NAME
                FixType.INLINE -> {
                    val lambdaArgument = element.lambdaArguments.single() ?: return
                    val functionLiteral = lambdaArgument.getLambdaExpression()?.functionLiteral ?: return
                    val bodyExpression = functionLiteral.bodyExpression ?: return

                    val statement = bodyExpression.statements.first()
                    element.replace(statement)
                    return
                }
            }

            val psiFactory = KtPsiFactory(project)
            val newLabeledExpression = (psiFactory.createExpression("return@$replacement") as KtReturnExpression).labeledExpression!!

            writableLabelReferenceExpressions.forEach { labelReferenceExpression ->
                labelReferenceExpression.replace(newLabeledExpression)
            }

            callNameExpression.replace(psiFactory.createExpression(replacement))
        }
    }
}

private fun KaSession.isRunBlocking(function: KaNamedFunctionSymbol): Boolean {
    if (function.importableFqName != RUN_BLOCKING_FQ_NAME) return false

    if (function.valueParameters.size != 2) return false

    fun checkFirstParameter(): Boolean {
        val parameter = function.valueParameters.first()
        val symbol = parameter.returnType.symbol ?: return false
        return symbol.importableFqName == COROUTINE_CONTEXT_FQ_NAME
    }

    fun checkSecondParameter(): Boolean {
        val parameter = function.valueParameters.last()
        val parameterType = parameter.returnType
        if (!parameterType.isSuspendFunctionType) return false
        val receiverSymbol = (parameterType as KaFunctionType).receiverType?.symbol ?: return false
        return receiverSymbol.importableFqName == COROUTINE_SCOPE_FQ_NAME
    }

    return checkFirstParameter() && checkSecondParameter()
}


private fun KtCallExpression.resolveToFunctionSymbol(analysisSession: KaSession): KaNamedFunctionSymbol? = with(analysisSession) {
    calleeExpression?.mainReference?.resolveToSymbol() as? KaNamedFunctionSymbol
}

/**
 * Checks if the given element is in a suspend context (either in a suspend function or in a suspend lambda).
 */
private fun KaSession.isInSuspendContext(element: KtExpression): Boolean {
    for (parent in element.parents(withSelf = false)) {
        when (parent) {
            is KtFunctionLiteral -> {
                // Skip lambdas which happen to be fully locally inlined
                if (isInlinedArgument(parent, allowCrossinline = false)) {
                    continue
                }

                // Check if the matching parameter's return type is a suspend type
                val (_, argumentSymbol) = getCallExpressionSymbol(parent) ?: return false
                val parameterType = argumentSymbol.returnType

                return parameterType.isSuspendFunctionType
            }

            is KtFunction -> {
                return parent.modifierList?.hasSuspendModifier() == true
            }
        }
    }

    return false
}

