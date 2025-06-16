// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.buildStringTemplateForBinaryExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.canConvertToStringTemplate
import org.jetbrains.kotlin.idea.codeinsights.impl.base.containNoNewLine
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isFirstStringPlusExpressionWithoutNewLineInOperands
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class ConvertToStringTemplateInspection :
  KotlinApplicableInspectionBase.Simple<KtBinaryExpression, ConvertToStringTemplateInspection.Context>() {

    override fun buildVisitor(
      holder: ProblemsHolder,
      isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    data class Context(val replacement: SmartPsiElementPointer<KtStringTemplateExpression>)

    override fun createQuickFix(
      element: KtBinaryExpression,
      context: Context,
    ): KotlinModCommandQuickFix<KtBinaryExpression> = object : KotlinModCommandQuickFix<KtBinaryExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("convert.concatenation.to.template")

        override fun applyFix(
          project: Project,
          element: KtBinaryExpression,
          updater: ModPsiUpdater,
        ) {
            context.replacement.element?.let { element.replaced(it) }
        }
    }

    override fun KaSession.prepareContext(element: KtBinaryExpression): Context? {
        if (!canConvertToStringTemplate(element) || !isFirstStringPlusExpressionWithoutNewLineInOperands(element)) return null
        return Context(buildStringTemplateForBinaryExpression(element).createSmartPointer())
    }

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        if (element.operationToken != KtTokens.PLUS) return false
        return element.containNoNewLine()
    }

    override fun getProblemDescription(element: KtBinaryExpression, context: Context): String =
        KotlinBundle.message("convert.concatenation.to.template.before.text")
}