// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.liveTemplates

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TemplateContext
import com.intellij.codeInsight.template.impl.TemplateOptionalProcessor
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilBase
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.psi.KtFile

class KotlinShortenFQNamesProcessor : TemplateOptionalProcessor {
    override fun processText(project: Project, template: Template, document: Document, templateRange: RangeMarker, editor: Editor) {
        if (!template.isToShortenLongNames) return

        PsiDocumentManager.getInstance(project).commitDocument(document)

        val file = PsiUtilBase.getPsiFileInEditor(editor, project)
        val (ktFile, range) = getMaybeInjectedRange(file, templateRange.textRange) ?: return
        ShortenReferencesFacility.getInstance().shorten(ktFile, range)

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document)
    }

    override fun getOptionName(): String {
        return CodeInsightBundle.message("dialog.edit.template.checkbox.shorten.fq.names")
    }

    override fun isEnabled(template: Template): Boolean = template.isToShortenLongNames
    override fun setEnabled(template: Template, value: Boolean) {}
    override fun isVisible(template: Template, context: TemplateContext): Boolean = false

    private fun getMaybeInjectedRange(file: PsiFile?, textRange: TextRange): FileWithRange? {
        var ktFile = file as? KtFile
        var range = textRange
        if (file != null && ktFile == null) {
            val project = file.project
            val manager = InjectedLanguageManager.getInstance(project)
            val psiElement = manager.findInjectedElementAt(file, range.startOffset)

            if (psiElement != null) {
                val documentWindow = PsiDocumentManager.getInstance(project)
                    .getCachedDocument(psiElement.containingFile) as? DocumentWindow
                if (documentWindow != null) {
                    val rangeStart = documentWindow.hostToInjected(range.startOffset)
                    val rangeEnd = documentWindow.hostToInjected(range.endOffset)
                    if (rangeStart != -1 && rangeEnd != -1) {
                        ktFile = psiElement.containingFile as? KtFile
                        range = TextRange(rangeStart, rangeEnd)
                    }
                }
            }
        }
        if (ktFile == null) return null
        return FileWithRange(ktFile, range)
    }

    private data class FileWithRange(
        val ktFile: KtFile,
        val textRange: TextRange,
    )
}
