// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.canDropCurlyBrackets
import org.jetbrains.kotlin.idea.base.psi.dropCurlyBracketsIfPossible
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry

class RemoveCurlyBracesFromTemplateInspection(@JvmField var reportWithoutWhitespace: Boolean = false) :
    AbstractApplicabilityBasedInspection<KtBlockStringTemplateEntry>(KtBlockStringTemplateEntry::class.java) {
    override fun inspectionText(element: KtBlockStringTemplateEntry): String =
        KotlinBundle.message("redundant.curly.braces.in.string.template")

    override fun inspectionHighlightType(element: KtBlockStringTemplateEntry) =
        if (reportWithoutWhitespace || element.hasWhitespaceAround()) ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        else ProblemHighlightType.INFORMATION

    override val defaultFixText: String get() = KotlinBundle.message("remove.curly.braces")

    override fun isApplicable(element: KtBlockStringTemplateEntry): Boolean = element.canDropCurlyBrackets()

    override fun applyTo(element: KtBlockStringTemplateEntry, project: Project, editor: Editor?) {
        element.dropCurlyBracketsIfPossible()
    }

    override fun createOptionsPanel() = MultipleCheckboxOptionsPanel(this).apply {
        addCheckbox(KotlinBundle.message("report.also.for.a.variables.without.a.whitespace.around"), "reportWithoutWhitespace")
    }
}

private fun KtBlockStringTemplateEntry.hasWhitespaceAround(): Boolean =
    prevSibling?.isWhitespaceOrQuote(true) == true && nextSibling?.isWhitespaceOrQuote(false) == true

private fun PsiElement.isWhitespaceOrQuote(prev: Boolean): Boolean {
    val char = if (prev) text.lastOrNull() else text.firstOrNull()
    return char != null && (char.isWhitespace() || char == '"')
}
