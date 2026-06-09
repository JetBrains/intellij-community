// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModChooseAction
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaImplicitReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.KaReceiverValue
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaContextParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaReceiverParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.contextParameters
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.analysis.api.utils.unwrapSmartCasts
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.intentions.contexts.ContextParameterUtils.isKotlinContextCall
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor

/**
 * Inspection that detects implicit context arguments and offers to convert them to explicit
 * named arguments. This is the opposite of [ConvertExplicitContextArgumentToImplicitInspection].
 */
internal class ConvertImplicitContextArgumentToExplicitInspection :
    KotlinApplicableInspectionBase<KtCallExpression, ConvertImplicitContextArgumentToExplicitInspection.Context>() {
    override fun InspectionManager.createProblemDescriptor(
        element: KtCallExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val fix = LocalQuickFix.from(ChooseContextArgumentsFix(element, context))
        if (fix == null) {
            Logger.getInstance(javaClass).error("Unexpected null quick fix for non-null action")
        }

        return createProblemDescriptor(
            element,
            rangeInElement,
            KotlinBundle.message("inspection.convert.implicit.context.argument.to.explicit.display.name"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            onTheFly,
            *listOfNotNull(fix).toTypedArray()
        )
    }

    data class Context(
        val contextArgumentsToAdd: List<Pair<Name, String>>,
        val removeEnclosingContextBlock: Boolean,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean =
        element.languageVersionSettings.supportsFeature(LanguageFeature.ExplicitContextArguments) &&
                element.calleeExpression != null

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val resolvedCall = element.resolveToCall()?.singleFunctionCallOrNull() ?: return null
        val contextParameters = resolvedCall.symbol.contextParameters
        val contextArguments = resolvedCall.contextArguments
        if (contextArguments.size != contextParameters.size) return null

        val argumentsToAdd = mutableListOf<Pair<Name, String>>()

        for ((index, contextParam) in contextParameters.withIndex()) {
            val paramName = contextParam.name
            if (paramName.isSpecial || paramName.asString() == "_") continue

            val existingExplicitArg = element.valueArguments.find {
                it.getArgumentName()?.asName == paramName
            }
            if (existingExplicitArg != null) continue

            val contextArg = contextArguments.getOrNull(index) ?: continue
            val replacement = createReplacementForContextArgument(element, contextArg, contextParam.returnType) ?: continue

            argumentsToAdd.add(paramName to replacement)
        }

        if (argumentsToAdd.isEmpty()) return null
        val removeContextBlock = isSingleUsageContext(element)

        return Context(argumentsToAdd, removeContextBlock)
    }

    private class ChooseContextArgumentsFix(
        element: KtCallExpression,
        private val context: Context
    ) : PsiBasedModCommandAction<KtCallExpression>(element, KtCallExpression::class.java) {

        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.convert.implicit.context.argument.to.explicit.choose.title")

        override fun perform(context: ActionContext, element: KtCallExpression): ModCommand {
            val (contextArgumentsToAdd, removeEnclosingContextBlock) = this.context
            val fixes = buildList {
                add(
                    ConvertAllArgumentsToExplicitFix(
                        element, contextArgumentsToAdd,
                        removeEnclosingContextBlock
                    )
                )
                if (contextArgumentsToAdd.size > 1) {
                    for ((name, replacement) in contextArgumentsToAdd) {
                        add(ConvertSingleArgumentToExplicitFix(element, name, replacement))
                    }
                }
            }
            return ModChooseAction(KotlinBundle.message("inspection.convert.implicit.context.argument.to.explicit.chooser.title"), fixes)
        }
    }

    private class ConvertSingleArgumentToExplicitFix(
        element: KtCallExpression,
        private val paramName: Name,
        private val replacement: String
    ) : PsiUpdateModCommandAction<KtCallExpression>(element) {

        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.convert.implicit.context.argument.to.explicit.fix.single", paramName.asString())

        override fun invoke(
            context: ActionContext,
            element: KtCallExpression,
            updater: ModPsiUpdater
        ) {
            appendArgumentToExpression(KtPsiFactory(context.project), replacement, paramName, element)
        }
    }

    private class ConvertAllArgumentsToExplicitFix(
        element: KtCallExpression,
        private val argumentsToAdd: List<Pair<Name, String>>,
        private val removeEnclosingContextBlock: Boolean
    ) : PsiUpdateModCommandAction<KtCallExpression>(element) {

        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.convert.implicit.context.argument.to.explicit.fix.text.all")

        override fun invoke(
            context: ActionContext,
            element: KtCallExpression,
            updater: ModPsiUpdater
        ) {
            val psiFactory = KtPsiFactory(context.project)

            for ((paramName, replacement) in argumentsToAdd) {
                appendArgumentToExpression(psiFactory, replacement, paramName, element)
            }

            if (removeEnclosingContextBlock) {
                removeEnclosingContextBlock(element)
            }
        }
    }
}

private fun appendArgumentToExpression(
    psiFactory: KtPsiFactory,
    replacement: String,
    paramName: Name,
    element: KtCallExpression
) {
    val newArgument = psiFactory.createArgument(
        psiFactory.createExpression(replacement),
        paramName
    )
    val argumentList = element.valueArgumentList
    if (argumentList != null) {
        argumentList.addArgument(newArgument)
    } else {
        element.addAfter(
            psiFactory.createCallArguments("(${paramName.asString()} = $replacement)"),
            element.calleeExpression
        )
    }
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.createReplacementForContextArgument(
    callExpression: KtCallExpression,
    receiverValue: KaReceiverValue,
    expectedType: KaType,
): String? {
    val unwrapped = receiverValue.unwrapSmartCasts()
    val symbol = (unwrapped as? KaImplicitReceiverValue)?.symbol ?: return null

    return when (symbol) {
        is KaReceiverParameterSymbol -> symbol.containingSymbol?.name?.asString()?.let { "this@$it" } ?: "this"

        is KaContextParameterSymbol -> {
            val name = symbol.name
            if (!name.isSpecial) {
                name.asString()
            } else {
                findExpressionInEnclosingContextBlock(callExpression, expectedType)
            }
        }

        else -> null
    }
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.findExpressionInEnclosingContextBlock(
    callExpression: KtCallExpression,
    expectedType: KaType,
): String? {
    var enclosingElement: PsiElement = callExpression
    while (true) {
        val lambdaExpr = enclosingElement.parentOfType<KtLambdaExpression>() ?: return null
        val lambdaArg = lambdaExpr.parent as? KtLambdaArgument ?: return null
        val contextCall = lambdaArg.parent as? KtCallExpression ?: return null

        if (isKotlinContextCall(contextCall)) {
            for (valueArg in contextCall.valueArguments) {
                val contextArgExpr = valueArg.getArgumentExpression() ?: continue
                val contextArgType = contextArgExpr.expressionType ?: continue
                if (contextArgType.isSubtypeOf(expectedType)) {
                    return contextArgExpr.text
                }
            }
        }
        enclosingElement = contextCall
    }
}

/**
 * Checks if the call is the only statement inside an enclosing `context()` block's lambda,
 * meaning the context block can be removed after converting to explicit arguments.
 */
@OptIn(KaExperimentalApi::class)
private fun KaSession.isSingleUsageContext(callExpression: KtCallExpression): Boolean {
    val lambdaExpr = callExpression.parentOfType<KtLambdaExpression>() ?: return false
    val lambdaArg = lambdaExpr.parent as? KtLambdaArgument ?: return false
    val contextCall = lambdaArg.parent as? KtCallExpression ?: return false

    if (!isKotlinContextCall(contextCall)) return false

    val bodyExpression = lambdaExpr.bodyExpression ?: return false
    val statements = bodyExpression.statements
    return statements.singleOrNull() == callExpression
}

/**
 * Removes the enclosing `context()` block, replacing it with just the call expression.
 * Preserves any comments that were inside the lambda body.
 */
private fun removeEnclosingContextBlock(callExpression: KtCallExpression) {
    val lambdaExpr = callExpression.parentOfType<KtLambdaExpression>() ?: return
    val lambdaArg = lambdaExpr.parent as? KtLambdaArgument ?: return
    val contextCall = lambdaArg.parent as? KtCallExpression ?: return

    val commentSaver = CommentSaver(contextCall)
    val replacement = callExpression.copy()
    val replaced = contextCall.replace(replacement)
    commentSaver.restore(replaced, preserveTrailingComments = true)
}