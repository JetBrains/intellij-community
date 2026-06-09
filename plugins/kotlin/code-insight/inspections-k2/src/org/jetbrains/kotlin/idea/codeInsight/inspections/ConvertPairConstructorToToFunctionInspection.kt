// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.callExpressionVisitor
import org.jetbrains.kotlin.psi.createExpressionByPattern

private val PAIR_FQ_NAME = FqName("kotlin.Pair")

internal class ConvertPairConstructorToToFunctionInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Unit,
    ): String = KotlinBundle.message("can.be.converted.to.to")

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        if (element.valueArguments.size != 2) return false
        if (element.valueArguments.mapNotNull { it.getArgumentExpression() }.size != 2) return false
        val callee = element.calleeExpression?.text ?: return false
        return callee == "Pair"
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtCallExpression): Unit? {
        val calleeSymbol = element.resolveSymbol() as? KaConstructorSymbol ?: return null
        if (calleeSymbol.importableFqName != PAIR_FQ_NAME) return null

        return Unit
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getFamilyName(): String =
            KotlinBundle.message("convert.pair.constructor.to.to.fix.text")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val args = element.valueArguments.mapNotNull { it.getArgumentExpression() }.toTypedArray()
            element.replace(KtPsiFactory(project).createExpressionByPattern("$0 to $1", *args))
        }
    }
}
