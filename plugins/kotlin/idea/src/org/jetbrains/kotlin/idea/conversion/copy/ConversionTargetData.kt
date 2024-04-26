// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.conversion.copy

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

internal data class ConversionTargetData(
    val ktFile: KtFile,
    val targetBounds: RangeMarker,
    val targetDocument: Document,
)

internal fun getTargetData(project: Project, editor: Editor, caretOffset: Int, bounds: RangeMarker): ConversionTargetData? {
    val topLevelDocument = editor.document
    val psiDocumentManager = PsiDocumentManager.getInstance(project)

    val topLevelPsiFile = psiDocumentManager.getPsiFile(topLevelDocument) ?: return null

    var newBounds = bounds
    var newDocument = topLevelDocument

    val ktFile: KtFile? = topLevelPsiFile.safeAs<KtFile>() ?: run {
        val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
        val injectedDocuments = injectedLanguageManager.getCachedInjectedDocumentsInRange(topLevelPsiFile, TextRange(caretOffset, caretOffset + 1))
        val injectedDocument = injectedDocuments.firstOrNull() ?: return@run null
        val injectedPsiFile = psiDocumentManager.getPsiFile(injectedDocument)?.safeAs<KtFile>() ?: return@run null

        newBounds = injectedDocument.createRangeMarker(
            TextRange(
                injectedDocument.hostToInjected(bounds.startOffset),
                injectedDocument.hostToInjected(bounds.endOffset)
            )
        )
        newDocument = injectedDocument

        injectedPsiFile
    }

    val targetFile = ktFile?.takeIf { it.virtualFile.isWritable } ?: return null

    return ConversionTargetData(
        targetFile,
        newBounds,
        newDocument,
    )
}
