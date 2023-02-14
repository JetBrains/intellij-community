// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.cutPaste

import com.intellij.codeInsight.editorActions.CopyPastePostProcessor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.elementsInRange
import java.awt.datatransfer.Transferable

class MoveDeclarationsCopyPasteProcessor : CopyPastePostProcessor<MoveDeclarationsTransferableData>() {
    companion object {
        private val LOG = Logger.getInstance(MoveDeclarationsCopyPasteProcessor::class.java)

        fun rangeToDeclarations(file: KtFile, range: TextRange): List<KtNamedDeclaration> {
            val elementsInRange = file.elementsInRange(range)
            val meaningfulElements = elementsInRange.filterNot { it is PsiWhiteSpace || it is PsiComment }
            if (meaningfulElements.isEmpty()) return emptyList()
            if (!meaningfulElements.all { it is KtNamedDeclaration }) return emptyList()
            @Suppress("UNCHECKED_CAST")
            return meaningfulElements as List<KtNamedDeclaration>
        }
    }

    override fun collectTransferableData(
        file: PsiFile,
        editor: Editor,
        startOffsets: IntArray,
        endOffsets: IntArray
    ): List<MoveDeclarationsTransferableData> {
        if (DumbService.isDumb(file.project)) return emptyList()

        if (file !is KtFile) return emptyList()
        if (startOffsets.size != 1) return emptyList()

        val declarations = rangeToDeclarations(file, TextRange(startOffsets[0], endOffsets[0]))
        if (declarations.isEmpty()) return emptyList()

        val parent = declarations.asSequence().map { it.parent }.distinct().singleOrNull() ?: return emptyList()
        val sourceObjectFqName = when (parent) {
            is KtFile -> null
            is KtClassBody -> (parent.parent as? KtObjectDeclaration)?.fqName?.asString() ?: return emptyList()
            else -> return emptyList()
        }

        if (declarations.any { it.name == null }) return emptyList()

        val imports = file.importDirectives.map { it.text }

        return listOf(
            MoveDeclarationsTransferableData(
                file.virtualFile.url,
                sourceObjectFqName,
                declarations.map { it.text },
                imports
            )
        )
    }

    override fun extractTransferableData(content: Transferable): List<MoveDeclarationsTransferableData> {
        try {
            if (content.isDataFlavorSupported(MoveDeclarationsTransferableData.DATA_FLAVOR)) {
                return listOf(content.getTransferData(MoveDeclarationsTransferableData.DATA_FLAVOR) as MoveDeclarationsTransferableData)
            }
        } catch (e: Throwable) {
            if (e is ControlFlowException) throw e
            LOG.error(e)
        }
        return emptyList()
    }

    override fun processTransferableData(
        project: Project,
        editor: Editor,
        bounds: RangeMarker,
        caretOffset: Int,
        indented: Ref<in Boolean>,
        values: List<MoveDeclarationsTransferableData>
    ) {
        val data = values.single()

        fun putCookie() {
            if (bounds.isValid) {
                val cookie =
                    MoveDeclarationsEditorCookie(data, bounds, PsiModificationTracker.getInstance(project).modificationCount)
                editor.putUserData(MoveDeclarationsEditorCookie.KEY, cookie)
            }
        }

        if (isUnitTestMode()) {
            putCookie()
        } else {
            // in real application we put cookie later to allow all other paste handlers do their work (because modificationCount will change)
            ApplicationManager.getApplication().invokeLater(::putCookie)
        }
    }
}

