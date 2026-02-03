// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.canDropCurlyBrackets
import org.jetbrains.kotlin.idea.base.psi.dropCurlyBracketsIfPossible
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class RemoveCurlyBracesFromTemplateInspection(@JvmField var reportWithoutWhitespace: Boolean = false) :
    KotlinApplicableInspectionBase.Simple<KtBlockStringTemplateEntry, Unit>() {

    override fun KaSession.prepareContext(element: KtBlockStringTemplateEntry): Unit = Unit

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitBlockStringTemplateEntry(element: KtBlockStringTemplateEntry) {
            visitTargetElement(element, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(element: KtBlockStringTemplateEntry, context: Unit): String =
        KotlinBundle.message("redundant.curly.braces.in.string.template")

    override fun getProblemHighlightType(element: KtBlockStringTemplateEntry, context: Unit): ProblemHighlightType =
        if (reportWithoutWhitespace || element.hasWhitespaceAround()) ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        else ProblemHighlightType.INFORMATION

    override fun isApplicableByPsi(element: KtBlockStringTemplateEntry): Boolean = element.canDropCurlyBrackets()

    override fun createQuickFix(element: KtBlockStringTemplateEntry, context: Unit): KotlinModCommandQuickFix<KtBlockStringTemplateEntry> =
        object : KotlinModCommandQuickFix<KtBlockStringTemplateEntry>() {
            override fun getFamilyName(): String = KotlinBundle.message("remove.curly.braces")

            override fun applyFix(project: Project, element: KtBlockStringTemplateEntry, updater: ModPsiUpdater) {
                element.dropCurlyBracketsIfPossible()
            }
        }

    override fun getOptionsPane() = pane(
        checkbox("reportWithoutWhitespace", KotlinBundle.message("report.also.for.a.variables.without.a.whitespace.around"))
    )
}

private fun KtBlockStringTemplateEntry.hasWhitespaceAround(): Boolean =
    prevSibling?.isWhitespaceOrQuote(true) == true && nextSibling?.isWhitespaceOrQuote(false) == true

private fun PsiElement.isWhitespaceOrQuote(prev: Boolean): Boolean {
    val char = if (prev) text.lastOrNull() else text.firstOrNull()
    return char != null && (char.isWhitespace() || char == '"')
}
