// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.resolveSymbol
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.deleteValueArgument
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.intentions.contexts.ContextParameterUtils.consumedContextParameters
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames.contextCallableId
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtExperimentalApi
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtExpressionWithLabel
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

internal class UnusedContextParameterCallInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, UnusedContextParameterCallInspection.BodyContext>() {

    data class BodyContext(
        val unusedArguments: List<SmartPsiElementPointer<KtValueArgument>>,
        val allArgumentsUnused: Boolean,
        val jumpsTargetingLambda: List<SmartPsiElementPointer<KtExpressionWithLabel>>,
    )

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        if (element.calleeExpression?.text != "context") return false
        val lambda = element.contextLambda() ?: return false
        val nonLambdaArgs = element.valueArguments.mapNotNull { arg ->
            arg.getArgumentExpression()?.takeUnless { it == lambda }
        }
        return (nonLambdaArgs.isNotEmpty())
    }

    // Trailing or non-trailing
    private fun KtCallExpression.contextLambda(): KtLambdaExpression? =
        lambdaArguments.lastOrNull()?.getLambdaExpression()
            ?: (valueArguments.lastOrNull()?.getArgumentExpression() as? KtLambdaExpression)


    override fun isAvailableForFile(file: PsiFile): Boolean {
        return super.isAvailableForFile(file) && file.languageVersionSettings.supportsFeature(LanguageFeature.ContextParameters)
    }

    override fun getProblemDescription(element: KtCallExpression, context: BodyContext): @InspectionMessage String =
        KotlinBundle.message("inspection.unused.context.parameter.call.description")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        callExpressionVisitor { visitTargetElement(it, holder, isOnTheFly) }

    context(session: KaSession)
    override fun prepareContext(element: KtCallExpression): BodyContext? {
        val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        if (resolvedCall.symbol.callableId != contextCallableId) return null
        val lambda = element.contextLambda() ?: return null
        val contextParameters = lambda.functionLiteral.symbol.contextParameters
        if (contextParameters.isEmpty()) return null
        if (lambda.bodyExpression == null) return null

        val nonLambdaArguments = element.valueArguments.filter {
            it.getArgumentExpression() != lambda
        }
        if (nonLambdaArguments.size != contextParameters.size) return null

        val allSideEffectFree = nonLambdaArguments.all { argument ->
            val expression = argument.getArgumentExpression() ?: return@all false
            isSideEffectFree(expression)
        }
        if (!allSideEffectFree) return null
        val body = lambda.bodyExpression ?: return null
        val consumed = consumedContextParameters(body, contextParameters)

        val unusedArguments = contextParameters.indices
            .filter { contextParameters[it] !in consumed }
            .map { nonLambdaArguments[it] }
        if (unusedArguments.isEmpty()) return null

        val jumpsTargetingLambda = body.collectJumpsTargetingLambda(lambda)

        val pointerManager = SmartPointerManager.getInstance(element.project)

        return BodyContext(
            unusedArguments = unusedArguments.map { pointerManager.createSmartPsiElementPointer(it) },
            allArgumentsUnused = consumed.isEmpty(),
            jumpsTargetingLambda = jumpsTargetingLambda.map { pointerManager.createSmartPsiElementPointer(it) },
        )
    }

    @OptIn(KaExperimentalApi::class, KtExperimentalApi::class)
    context(session: KaSession)
    private fun isSideEffectFree(expression: KtExpression): Boolean {
        return when (val unwrapped = KtPsiUtil.deparenthesize(expression)) {
            is KtConstantExpression -> true
            is KtThisExpression -> true
            is KtStringTemplateExpression ->
                unwrapped.entries.all { it is KtLiteralStringTemplateEntry || it is KtEscapeStringTemplateEntry }

            is KtSimpleNameExpression -> {
                val symbol = unwrapped.resolveSymbol()
                symbol is KaLocalVariableSymbol || symbol is KaValueParameterSymbol
            }

            else -> false
        }
    }

    private fun KtBlockExpression.collectJumpsTargetingLambda(lambda: KtLambdaExpression): List<KtExpressionWithLabel> {
        val functionLiteral = lambda.functionLiteral
        val result = mutableListOf<KtExpressionWithLabel>()
        forEachDescendantOfType<KtExpressionWithLabel> { labeled ->
            if (labeled !is KtReturnExpression && labeled !is KtBreakExpression && labeled !is KtContinueExpression) {
                return@forEachDescendantOfType
            }
            val targetLabel = labeled.getTargetLabel() ?: return@forEachDescendantOfType
            val resolved = targetLabel.mainReference.resolve()
            if (resolved == functionLiteral) {
                result += labeled
            }
        }
        return result
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: BodyContext
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getFamilyName(): @InspectionMessage String =
            KotlinBundle.message("inspection.unused.context.parameter.call.fix.family.name")

        override fun getName(): @InspectionMessage String =
            if (context.allArgumentsUnused) {
                KotlinBundle.message("inspection.unused.context.parameter.call.fix.family.name")
            } else {
                KotlinBundle.message("inspection.unused.context.parameter.call.fix.name.partial")
            }

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater
        ) {
            if (context.allArgumentsUnused) {
                removeWholeContextCall(project, element, updater)
            } else {
                removeUnusedArguments(element, updater)
            }
        }

        private fun removeUnusedArguments(
            element: KtCallExpression,
            updater: ModPsiUpdater
        ) {
            val argumentList = element.valueArgumentList ?: return
            val writableArguments = context.unusedArguments.mapNotNull { pointer ->
                pointer.element?.let { updater.getWritable(it) }
            }
            writableArguments.forEach { argument ->
                argumentList.deleteValueArgument(argument)
            }
        }

        private fun removeWholeContextCall(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater
        ) {
            val psiFactory = KtPsiFactory(project)
            val body = element.contextLambda()?.bodyExpression ?: return
            val statements = body.statements
            val target = element.getQualifiedExpressionForSelectorOrThis()
            val parent = target.parent

            val hasLambdaJump = context.jumpsTargetingLambda.isNotEmpty()
            when {
                statements.isEmpty() -> {
                    if (parent is KtBlockExpression) target.delete()
                    else target.replace(psiFactory.createExpression("Unit"))
                }

                parent is KtBlockExpression && !hasLambdaJump -> {
                    parent.addRangeBefore(statements.first(), statements.last(), target)
                    target.delete()
                }

                statements.size == 1 && !hasLambdaJump -> {
                    target.replace(statements.single())
                }

                else -> {
                    context.jumpsTargetingLambda.forEach { pointer ->
                        val originalJump = pointer.element ?: return@forEach
                        val writableJump = updater.getWritable(originalJump)
                        val labelName = writableJump.getTargetLabel()?.getReferencedName() ?: return@forEach
                        writableJump.replace(
                            psiFactory.createExpression(writableJump.text.replaceFirst("@$labelName", "@run"))
                        )
                    }
                    element.valueArgumentList?.delete()
                    element.calleeExpression?.replace(psiFactory.createExpression("run"))
                }
            }
        }
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> {
        return ApplicabilityRanges.calleeExpression(element)
    }
}
