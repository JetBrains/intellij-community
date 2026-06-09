// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaExplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.contextParameters
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.relativeTo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.intentions.contexts.ContextParameterUtils.isKotlinContextCall
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.valueArgumentVisitor

/**
 * Inspection that detects explicit context arguments that can be converted to implicit ones
 * by wrapping the call in a `context(...) { ... }` block, or by simply removing the argument
 * if the value is already available in the enclosing context.
 */
internal class ConvertExplicitContextArgumentToImplicitInspection :
    KotlinApplicableInspectionBase.Simple<KtValueArgument, ConvertExplicitContextArgumentToImplicitInspection.Context>() {

    data class Context(
        val argumentExpressionText: String,
        val isValueAlreadyInContext: Boolean,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = valueArgumentVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(element: KtValueArgument, context: Context): @InspectionMessage String =
        KotlinBundle.message("inspection.convert.explicit.context.argument.to.implicit.display.name")

    override fun getApplicableRanges(element: KtValueArgument): List<TextRange> {
        val argumentExpression = element.getArgumentExpression() ?: return emptyList()
        val textRange = TextRange(element.startOffset, argumentExpression.startOffset).relativeTo(element)
        return listOf(textRange)
    }

    override fun isApplicableByPsi(element: KtValueArgument): Boolean {
        if (!element.languageVersionSettings.supportsFeature(LanguageFeature.ExplicitContextArguments)) return false
        if (!element.isNamed()) return false
        if (element.getArgumentExpression() == null) return false

        val argumentList = element.parent as? KtValueArgumentList ?: return false
        return argumentList.parent is KtCallExpression
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtValueArgument): Context? {
        val argumentExpression = element.getArgumentExpression() ?: return null
        val callExpression = element.getStrictParentOfType<KtCallExpression>() ?: return null

        val originalCall = callExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        val contextParameter = originalCall.contextArgumentMapping[argumentExpression] ?: return null

        val expectedPsi = when (argumentExpression) {
            is KtSimpleNameExpression -> argumentExpression.mainReference.resolveToSymbol()?.psi
            is KtThisExpression -> argumentExpression.instanceReference.mainReference.resolveToSymbol()?.psi
            else -> null
        }

        if (expectedPsi != null && contextWrapNotNeeded(callExpression, element, expectedPsi, contextParameter.returnType)) {
            return Context(argumentExpression.text, isValueAlreadyInContext = true)
        }

        val callOrQualified = callExpression.getQualifiedExpressionForSelectorOrThis()
        if (callExpression.wouldChangeEvaluationOrder(element, callOrQualified)) return null
        if (!callExpression.resolvesToSameTargetWhenWrapped(element, argumentExpression.text, callOrQualified)) return null

        return Context(argumentExpression.text, isValueAlreadyInContext = false)
    }

    override fun createQuickFix(
        element: KtValueArgument,
        context: Context,
    ): KotlinModCommandQuickFix<KtValueArgument> = object : KotlinModCommandQuickFix<KtValueArgument>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.convert.explicit.context.argument.to.implicit.fix.text")

        override fun applyFix(
            project: Project,
            element: KtValueArgument,
            updater: ModPsiUpdater,
        ) {
            val argumentList = element.parent as? KtValueArgumentList ?: return
            argumentList.removeArgument(element)

            if (context.isValueAlreadyInContext) return

            val callExpression = argumentList.parent as? KtCallExpression ?: return
            val callOrQualified = callExpression.getQualifiedExpressionForSelectorOrThis()

            val psiFactory = KtPsiFactory(project)
            val wrappedExpression = psiFactory.createExpression(
                "context(${context.argumentExpressionText}) {\n${callOrQualified.text}\n}"
            )

            val result = callOrQualified.replace(wrappedExpression)
            CodeStyleManager.getInstance(project).reformat(result)
        }
    }
}

private fun KtCallExpression.wouldChangeEvaluationOrder(
    selectedArgument: KtValueArgument,
    callOrQualified: KtExpression,
): Boolean {
    val selectedArgumentIndex = valueArguments.indexOf(selectedArgument)
    if (selectedArgumentIndex < 0) return true

    val contextArgumentExpression = selectedArgument.getArgumentExpression()
    if (contextArgumentExpression != null && !contextArgumentExpression.isSafeToEvaluateAfterContextArgument()) return true

    val explicitReceiver = (callOrQualified as? KtQualifiedExpression)?.receiverExpression
    if (explicitReceiver != null && !explicitReceiver.isSafeToEvaluateAfterContextArgument()) return true

    for (i in 0 until selectedArgumentIndex) {
        val expr = valueArguments[i].getArgumentExpression() ?: continue
        if (!expr.isSafeToEvaluateAfterContextArgument()) return true
    }
    return false
}

private fun KtExpression.isSafeToEvaluateAfterContextArgument(): Boolean =
    when (val expression = KtPsiUtil.safeDeparenthesize(this)) {
        is KtConstantExpression -> true
        is KtSimpleNameExpression -> true
        is KtStringTemplateExpression -> !expression.hasInterpolation()
        is KtThisExpression -> true
        else -> false
    }

private fun KtCallExpression.buildCallTextWithoutArgument(selectedArgument: KtValueArgument): String {
    val callee = calleeExpression?.text ?: ""
    val typeArgs = typeArgumentList?.text.orEmpty()
    val remainingArgs = valueArguments.filter { it != selectedArgument }.joinToString(", ") { it.text }
    val lambdaArgs = lambdaArguments.joinToString(" ") { it.text }
    return "$callee$typeArgs($remainingArgs)$lambdaArgs"
}

private fun KtCallExpression.resolvesToSameTargetWhenWrapped(
    selectedArgument: KtValueArgument,
    contextArgumentText: String,
    callOrQualified: KtExpression,
): Boolean {
    val receiver = (callOrQualified as? KtQualifiedExpression)?.run { receiverExpression.text + operationSign.value }.orEmpty()
    val callTextWithoutArg = buildCallTextWithoutArgument(selectedArgument)
    val wrappedExpressionText = "context($contextArgumentText) {\n$receiver$callTextWithoutArg\n}"

    val codeFragment = KtPsiFactory(project).createExpressionCodeFragment(wrappedExpressionText, this)
    val contextCall = codeFragment.getContentElement() as? KtCallExpression ?: return false
    val lambdaBody = contextCall.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression ?: return false
    val wrappedCall = when (val firstStatement = lambdaBody.firstStatement) {
        is KtCallExpression -> firstStatement
        is KtQualifiedExpression -> firstStatement.selectorExpression as? KtCallExpression
        else -> null
    } ?: return false

    return analyze(wrappedCall) {
        val originalSymbol = calleeExpression?.mainReference?.resolveToSymbol() ?: return@analyze false
        val wrappedSymbol = wrappedCall.calleeExpression?.mainReference?.resolveToSymbol()
        wrappedSymbol == originalSymbol
    }
}

private fun KaSession.contextWrapNotNeeded(
    callExpression: KtCallExpression,
    selectedArgument: KtValueArgument,
    expectedPsi: PsiElement,
    expectedType: KaType,
): Boolean {
    return isValueInEnclosingContextBlock(callExpression, expectedPsi, expectedType) || isResolvedImplicitlyToSameValue(
        callExpression,
        selectedArgument,
        expectedPsi
    )
}

private fun KaSession.isValueInEnclosingContextBlock(
    callExpression: KtCallExpression,
    expectedPsi: PsiElement,
    expectedType: KaType,
): Boolean {
    var enclosingElement: PsiElement = callExpression
    while (true) {
        val lambdaExpr = enclosingElement.getStrictParentOfType<KtLambdaExpression>() ?: return false
        val lambdaArg = lambdaExpr.parent as? KtLambdaArgument ?: return false
        val contextCall = lambdaArg.parent as? KtCallExpression ?: return false

        if (isKotlinContextCall(contextCall)) {
            for (valueArg in contextCall.valueArguments) {
                val contextArgExpr = valueArg.getArgumentExpression() as? KtSimpleNameExpression ?: continue
                val contextArgType = contextArgExpr.expressionType ?: continue
                if (!contextArgType.isSubtypeOf(expectedType)) continue
                if (contextArgExpr.mainReference.resolveToSymbol()?.psi == expectedPsi) {
                    return true
                }
            }
        }
        enclosingElement = contextCall
    }
}

@OptIn(KaExperimentalApi::class)
private fun isResolvedImplicitlyToSameValue(
    callExpression: KtCallExpression,
    selectedArgument: KtValueArgument,
    expectedPsi: PsiElement,
): Boolean {
    val callTextWithoutArg = callExpression.buildCallTextWithoutArgument(selectedArgument)
    val codeFragment = KtPsiFactory(callExpression.project).createExpressionCodeFragment(callTextWithoutArg, callExpression)
    val fragmentCall = codeFragment.getContentElement() as? KtCallExpression ?: return false
    val argumentName = selectedArgument.getArgumentName()?.asName ?: return false

    return analyze(fragmentCall) {
        // checking the ambiguity
        val resolvedCall = fragmentCall.resolveToCall()?.singleFunctionCallOrNull() ?: return@analyze false

        // contextArguments is ordered to match contextParameters by index
        val parameterIndex = resolvedCall.symbol.contextParameters.indexOfFirst { it.name == argumentName }
        if (parameterIndex < 0) return@analyze false

        val implicitArgument = resolvedCall.contextArguments.getOrNull(parameterIndex) ?: return@analyze false
        val implicitSymbol = when (val unwrapped = implicitArgument.unwrapSmartCasts()) {
            is KaImplicitReceiverValue -> unwrapped.symbol
            is KaExplicitReceiverValue -> (unwrapped.expression as? KtSimpleNameExpression)?.mainReference?.resolveToSymbol()
            else -> null
        }
        implicitSymbol?.psi == expectedPsi
    }
}