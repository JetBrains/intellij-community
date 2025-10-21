// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern

class UnsafeCastWithReturnInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, Unit>() {
    override fun getProblemDescription(
      element: KtBinaryExpression,
      context: Unit
    ): @InspectionMessage String = KotlinBundle.message("replace.unsafe.cast.with.safe.one.text")

    override fun createQuickFix(
      element: KtBinaryExpression,
      context: Unit
    ): KotlinModCommandQuickFix<KtBinaryExpression> = object : KotlinModCommandQuickFix<KtBinaryExpression>() {
        override fun getFamilyName() = KotlinBundle.message("replace.with.safe.cast.text")

        override fun applyFix(
          project: Project,
          element: KtBinaryExpression,
          updater: ModPsiUpdater
        ) {
            val psiFactory = KtPsiFactory(project)
            val left = element.left as KtBinaryExpressionWithTypeRHS
            psiFactory.createExpressionByPattern("$0 as? $1", left.left, left.right as KtTypeReference).let {
                left.replace(it)
            }
        }
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        val left = element.left as? KtBinaryExpressionWithTypeRHS ?: return false

        if (left.right == null) return false
        if (left.operationReference.getReferencedName() != "as") return false

        if (element.operationReference.getReferencedName() != "?:") return false
        if (KtPsiUtil.deparenthesize(element.right) !is KtReturnExpression) return false
        if (element.parent !is KtVariableDeclaration && element.parent !is KtValueArgument) return false
        return true
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtBinaryExpression): Unit = Unit

    override fun buildVisitor(
      holder: ProblemsHolder,
      isOnTheFly: Boolean
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }
}