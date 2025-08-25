// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.psi.dropCurlyBracketsIfPossible
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.isToString
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class ReplaceToStringWithStringTemplateInspection : AbstractApplicabilityBasedInspection<KtDotQualifiedExpression>(
  KtDotQualifiedExpression::class.java
) {
    override fun isApplicable(element: KtDotQualifiedExpression): Boolean {
        if (element.receiverExpression !is KtReferenceExpression) return false
        if (element.parent is KtBlockStringTemplateEntry) return false
        return element.isToString()
    }

    override fun applyTo(element: KtDotQualifiedExpression, project: Project, editor: Editor?) {
        val variable = element.receiverExpression.text
        val replaced = element.replace(KtPsiFactory(project).createExpression($$"\"${$$variable}\""))
        val blockStringTemplateEntry = (replaced as? KtStringTemplateExpression)?.entries?.firstOrNull() as? KtBlockStringTemplateEntry
        blockStringTemplateEntry?.dropCurlyBracketsIfPossible()
    }

    override fun inspectionText(element: KtDotQualifiedExpression): String =
        KotlinBundle.message("inspection.replace.to.string.with.string.template.display.name")

    override val defaultFixText: String get() = KotlinBundle.message("replace.tostring.with.string.template")
}