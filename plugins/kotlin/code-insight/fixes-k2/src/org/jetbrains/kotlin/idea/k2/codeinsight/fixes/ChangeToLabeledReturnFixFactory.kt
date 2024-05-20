// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.ChangeToLabeledReturnUtils
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.renderer.render

internal object ChangeToLabeledReturnFixFactory {

    val nullForNonnullType = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.NullForNonnullType ->
        val returnExpression = getLambdaReturnExpression(diagnostic.psi) ?: return@ModCommandBased emptyList()
        getQuickFix(returnExpression)
    }

    val returnNotAllowed = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.ReturnNotAllowed ->
        val returnExpression = diagnostic.psi
        getQuickFix(returnExpression)
    }

    val returnTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.ReturnTypeMismatch ->
        val returnExpression = getLambdaReturnExpression(diagnostic.psi) ?: return@ModCommandBased emptyList()
        getQuickFix(returnExpression)
    }

    context(KtAnalysisSession)
    private fun getQuickFix(returnExpression: KtReturnExpression): List<ChangeToLabeledReturnFix> {
        val candidates = findAccessibleLabels(returnExpression)
        return candidates.map {
            ChangeToLabeledReturnFix(returnExpression, labeledReturn = "return@${it.render()}")
        }
    }

    context(KtAnalysisSession)
    private fun findAccessibleLabels(position: KtReturnExpression): List<Name> {
        val result = mutableListOf<Name>()
        for (parent in position.parentsWithSelf) {
            when (parent) {
                is KtClassOrObject -> break
                is KtFunctionLiteral -> {
                    val (label, call) = parent.findLabelAndCall()
                    if (label != null) {
                        result.add(label)
                    }

                    // check if the current function literal is inlined and stop processing outer declarations if it's not
                    val callee = call?.calleeExpression as? KtReferenceExpression ?: break
                    val symbol = callee.mainReference.resolveToSymbol()
                    if (!(symbol is KtFunctionSymbol && symbol.isInline)) break
                }

                else -> {}
            }
        }
        return result
    }

    context(KtAnalysisSession)
    private fun getLambdaReturnExpression(element: PsiElement): KtReturnExpression? {
        val returnExpression = element.getStrictParentOfType<KtReturnExpression>() ?: return null
        val lambda = returnExpression.getStrictParentOfType<KtLambdaExpression>() ?: return null
        val lambdaReturnType = lambda.functionLiteral.getReturnKtType()
        val returnType = returnExpression.returnedExpression?.getKtType() ?: return null
        if (!returnType.isSubTypeOf(lambdaReturnType)) return null
        return returnExpression
    }

    private class ChangeToLabeledReturnFix(
        element: KtReturnExpression,
        private val labeledReturn: String,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtReturnExpression, Unit>(element, Unit) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtReturnExpression,
            elementContext: Unit,
            updater: ModPsiUpdater,
        ) = ChangeToLabeledReturnUtils.applyTo(actionContext.project, element, labeledReturn)

        override fun getFamilyName(): String = KotlinBundle.message("fix.change.to.labeled.return.family")

        override fun getActionName(
            actionContext: ActionContext,
            element: KtReturnExpression,
            elementContext: Unit,
        ): String = KotlinBundle.message("fix.change.to.labeled.return.text", labeledReturn)
    }
}
