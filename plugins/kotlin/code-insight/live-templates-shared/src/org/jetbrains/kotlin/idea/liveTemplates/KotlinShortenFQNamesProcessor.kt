// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates

import com.intellij.codeInsight.template.impl.TemplateOptionalProcessor
import com.intellij.openapi.project.Project
import com.intellij.codeInsight.template.Template
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.Editor
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template.impl.TemplateContext
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtFile

class KotlinShortenFQNamesProcessor : TemplateOptionalProcessor {
    override fun processText(project: Project, template: Template, document: Document, templateRange: RangeMarker, editor: Editor) {
        if (!template.isToShortenLongNames) return

        PsiDocumentManager.getInstance(project).commitDocument(document)

        val file = PsiUtilBase.getPsiFileInEditor(editor, project) as? KtFile ?: return
        ShortenReferencesFacility.getInstance().shorten(file, templateRange.textRange)

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
    }

    override fun getOptionName(): String {
        return CodeInsightBundle.message("dialog.edit.template.checkbox.shorten.fq.names")
    }

    override fun isEnabled(template: Template): Boolean = template.isToShortenLongNames
    override fun setEnabled(template: Template, value: Boolean) {}
    override fun isVisible(template: Template, context: TemplateContext) = false
}
