// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.dropCurlyBracketsIfPossible
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isToString
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid


class ReplaceToStringWithStringTemplateInspection : KotlinApplicableInspectionBase.Simple<KtDotQualifiedExpression, Unit>() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        if (element.receiverExpression !is KtReferenceExpression) return null
        if (element.parent is KtBlockStringTemplateEntry) return null
        return element.isToString().asUnit
    }

    override fun getProblemDescription(element: KtDotQualifiedExpression, context: Unit): String =
        KotlinBundle.message("inspection.replace.to.string.with.string.template.display.name")

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = ReplaceToStringWithStringTemplateQuickFix()

    private class ReplaceToStringWithStringTemplateQuickFix : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.tostring.with.string.template")

        override fun applyFix(
            project: Project,
            element: KtDotQualifiedExpression,
            updater: ModPsiUpdater
        ) {
            val variable = element.receiverExpression.text
            val replaced = element.replace(KtPsiFactory(project).createExpression($$"\"${$$variable}\""))
            val blockStringTemplateEntry = (replaced as? KtStringTemplateExpression)?.entries?.firstOrNull() as? KtBlockStringTemplateEntry
            blockStringTemplateEntry?.dropCurlyBracketsIfPossible()
        }
    }
}