// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiBasedModCommandAction
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.resolution.resolveSuccessfulSymbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.base.psi.setFunctionTypeReference
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.intentions.branchedTransformations.isPure
import org.jetbrains.kotlin.idea.searching.inheritors.findHierarchyWithSiblings
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern

@ApiStatus.Internal
class MakeUnitQuickFix(
    element: KtNamedFunction,
) : PsiBasedModCommandAction<KtNamedFunction>(element) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("change.main.function.return.type.to.unit.fix.text2")

    override fun perform(
        context: ActionContext,
        element: KtNamedFunction,
    ): ModCommand {
        val functionsToProcess = sequenceOf(element) + element.findHierarchyWithSiblings().filterIsInstance<KtNamedFunction>()

        return ModCommand.psiUpdate(context) { updater: ModPsiUpdater ->
            val psiFactory = KtPsiFactory(context.project)
            val writableFunctions = functionsToProcess.mapNotNull { updater.getWritable(it) }.toList()
            writableFunctions.forEach { makeFunctionUnit(it, psiFactory) }
        }
    }
}

private fun makeFunctionUnit(
    function: KtNamedFunction,
    psiFactory: KtPsiFactory,
) {
    function.setFunctionTypeReference(null)
    rewriteFunctionBodyForUnitReturn(function, psiFactory)
}

private fun rewriteFunctionBodyForUnitReturn(
    function: KtNamedFunction,
    psiFactory: KtPsiFactory,
) {
    val returnExpressions = function.collectTargetedReturnExpressions()

    // Process from the end to avoid invalidating PSI elements that still
    // need to be visited.
    for (returnExpression in returnExpressions.asReversed()) {
        if (!returnExpression.isValid) continue
        replaceReturnExpression(function, returnExpression, psiFactory)
    }

    replaceExpressionBody(function, psiFactory)
}

private fun replaceReturnExpression(
    function: KtNamedFunction,
    returnExpression: KtReturnExpression,
    psiFactory: KtPsiFactory,
) {
    val returnedExpression = returnExpression.returnedExpression

    if (returnedExpression == null) {
        removeTrailingFunctionReturn(function, returnExpression)
        return
    }

    if (returnedExpression.isPure()) {
        returnedExpression.delete()
        removeTrailingFunctionReturn(function, returnExpression)
        return
    }

    val block = returnExpression.parent as? KtBlockExpression
    if (block != null) {
        block.addBefore(returnedExpression.copy(), returnExpression)
        block.addBefore(psiFactory.createNewLine(), returnExpression)

        returnedExpression.delete()
        removeTrailingFunctionReturn(function, returnExpression)
        return
    }

    val replacement = psiFactory.createExpressionByPattern(
        """
                    {
                        $0
                        return
                    }
                """.trimIndent(),
        returnedExpression,
    )

    returnExpression.replace(replacement)
}

private fun removeTrailingFunctionReturn(
    function: KtNamedFunction,
    returnExpression: KtReturnExpression,
) {
    if (returnExpression.getTargetLabel() != null) return

    val body = function.bodyExpression as? KtBlockExpression ?: return

    if (body.statements.lastOrNull() === returnExpression) {
        returnExpression.delete()
    }
}

private fun replaceExpressionBody(
    function: KtNamedFunction,
    psiFactory: KtPsiFactory,
) {
    if (function.hasBlockBody()) return

    val body = function.bodyExpression ?: return

    val newBody = psiFactory.createBlock("").apply {
        if (!body.isPure()) {
            addBefore(body.copy(), rBrace)
        }
    }

    function.equalsToken?.delete()
    body.replace(newBody)
}

@OptIn(KaExperimentalApi::class)
private fun KtNamedFunction.collectTargetedReturnExpressions(): List<KtReturnExpression> = buildList {
    val targetFunction = this@collectTargetedReturnExpressions

    analyze(targetFunction) {
        val functionSymbol = targetFunction.symbol

        targetFunction.bodyExpression?.accept(object : KtTreeVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                // Don't visit nested local functions.
            }

            override fun visitReturnExpression(expression: KtReturnExpression) {
                if (expression.resolveSuccessfulSymbol() == functionSymbol) {
                    add(expression)
                }
                super.visitReturnExpression(expression)
            }
        })
    }
}
