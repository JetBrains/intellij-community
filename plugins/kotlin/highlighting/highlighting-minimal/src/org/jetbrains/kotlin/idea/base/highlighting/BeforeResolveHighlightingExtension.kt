// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingPassBase
import org.jetbrains.kotlin.idea.highlighter.visitor.AbstractHighlightingVisitor

@ApiStatus.Internal
interface BeforeResolveHighlightingExtension {
    fun createVisitor(holder: HighlightInfoHolder): AbstractHighlightingVisitor

    companion object {
        val EP_NAME: ExtensionPointName<BeforeResolveHighlightingExtension> =
            ExtensionPointName.create<BeforeResolveHighlightingExtension>("org.jetbrains.kotlin.beforeResolveHighlightingVisitor")
    }
}


class KotlinBeforeResolveHighlightingPass(file: PsiFile, document: Document) : AbstractHighlightingPassBase(file, document) {
    override fun runAnnotatorWithContext(element: PsiElement, holder: HighlightInfoHolder) {
        val extensions = BeforeResolveHighlightingExtension.EP_NAME.extensionList.map { it.createVisitor(holder) }

        element.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                extensions.forEach(element::accept)
                super.visitElement(element)
            }
        })
    }

    class Factory : TextEditorHighlightingPassFactory, DumbAware {
        override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
            if (file.fileType != KotlinFileType.INSTANCE) return null
            return KotlinBeforeResolveHighlightingPass(file, editor.document)
        }
    }
}