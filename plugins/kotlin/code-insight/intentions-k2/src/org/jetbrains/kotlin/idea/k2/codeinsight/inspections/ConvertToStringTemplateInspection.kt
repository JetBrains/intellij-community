// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.buildStringTemplateForBinaryExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.canConvertToStringTemplate
import org.jetbrains.kotlin.idea.codeinsights.impl.base.containNoNewLine
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isFirstStringPlusExpressionWithoutNewLineInOperands
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class ConvertToStringTemplateInspection : AbstractKotlinApplicableInspectionWithContext<KtBinaryExpression, ConvertToStringTemplateInspection.Context>() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitBinaryExpression(expression: KtBinaryExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }
    class Context(val replacement: SmartPsiElementPointer<KtStringTemplateExpression>)

    override fun apply(element: KtBinaryExpression, context: Context, project: Project, updater: ModPsiUpdater) {
        context.replacement.element?.let { element.replaced(it) }
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtBinaryExpression): Context? {
        if (!canConvertToStringTemplate(element) || !isFirstStringPlusExpressionWithoutNewLineInOperands(element)) return null
        return Context(buildStringTemplateForBinaryExpression(element).createSmartPointer())
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtBinaryExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        if (element.operationToken != KtTokens.PLUS) return false
        return element.containNoNewLine()
    }

    override fun getProblemDescription(element: KtBinaryExpression, context: Context): String =
        KotlinBundle.message("convert.concatenation.to.template.before.text")

    override fun getActionFamilyName(): String = KotlinBundle.message("convert.concatenation.to.template")
}