// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ChangeToLabeledReturnFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.findLabelAndCall
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.renderer.render

internal object ChangeToLabeledReturnFixFactory {

    val nullForNonnullType = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NullForNonnullType ->
        val returnExpression = getLambdaReturnExpression(diagnostic.psi) ?: return@ModCommandBased emptyList()
        getQuickFix(returnExpression)
    }

    val returnNotAllowed = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnNotAllowed ->
        val returnExpression = diagnostic.psi
        getQuickFix(returnExpression)
    }

    val returnTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ReturnTypeMismatch ->
        val returnExpression = getLambdaReturnExpression(diagnostic.psi) ?: return@ModCommandBased emptyList()
        getQuickFix(returnExpression)
    }

    private fun KaSession.getQuickFix(returnExpression: KtReturnExpression): List<ChangeToLabeledReturnFix> {
        val candidates = findAccessibleLabels(returnExpression)
        return candidates.map {
            ChangeToLabeledReturnFix(returnExpression, labeledReturn = "return@${it.render()}")
        }
    }

    private fun KaSession.findAccessibleLabels(position: KtReturnExpression): List<Name> {
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
                    if (!(symbol is KaNamedFunctionSymbol && symbol.isInline)) break
                }

                else -> {}
            }
        }
        return result
    }

    private fun KaSession.getLambdaReturnExpression(element: PsiElement): KtReturnExpression? {
        val returnExpression = element.getStrictParentOfType<KtReturnExpression>() ?: return null
        val lambda = returnExpression.getStrictParentOfType<KtLambdaExpression>() ?: return null
        val lambdaReturnType = lambda.functionLiteral.returnType
        val returnType = returnExpression.returnedExpression?.expressionType ?: return null
        if (!returnType.isSubtypeOf(lambdaReturnType)) return null
        return returnExpression
    }
}
