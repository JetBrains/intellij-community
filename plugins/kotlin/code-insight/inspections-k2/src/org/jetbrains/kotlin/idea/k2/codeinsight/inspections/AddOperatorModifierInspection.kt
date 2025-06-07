// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.search.ExpectActualUtils.withExpectedActuals
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

class AddOperatorModifierInspection : KotlinApplicableInspectionBase.Simple<KtNamedFunction, Unit>() {
    override fun getProblemDescription(element: KtNamedFunction, context: Unit): @InspectionMessage String =
        KotlinBundle.message("function.should.have.operator.modifier")

    override fun isApplicableByPsi(element: KtNamedFunction): Boolean =
        element.nameIdentifier != null && !element.hasModifier(KtTokens.OPERATOR_KEYWORD)

    override fun createQuickFix(element: KtNamedFunction, context: Unit): KotlinModCommandQuickFix<KtNamedFunction> =
        object : KotlinModCommandQuickFix<KtNamedFunction>() {

            override fun getFamilyName(): String = KotlinBundle.message("add.operator.modifier")

            override fun applyFix(
                project: Project,
                element: KtNamedFunction,
                updater: ModPsiUpdater,
            ) {
                val originElement = PsiTreeUtil.findSameElementInCopy(element, element.containingFile.originalFile)
                val declarations = withExpectedActuals(originElement)
                for (declaration in declarations) {
                    updater.getWritable(declaration).addModifier(KtTokens.OPERATOR_KEYWORD)
                }
            }
        }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
            visitTargetElement(function, holder, isOnTheFly)
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtNamedFunction): Unit? {
        val canBeOperator = analyze(element) {
            var symbol = element.symbol as? KaNamedFunctionSymbol
            symbol?.canBeOperator == true && !symbol.isOperator
        }

        return if (canBeOperator) return Unit else null
    }
}
