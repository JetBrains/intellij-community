// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.highlighting.isNameHighlightingEnabled
import org.jetbrains.kotlin.idea.highlighting.HiglightingFactory
import org.jetbrains.kotlin.psi.KtElement

internal abstract class AfterResolveHighlighter(
    private val holder: AnnotationHolder,
    protected val project: Project,
) {

    context(KtAnalysisSession)
    abstract fun highlight(element: KtElement)

    private fun createInfoAnnotation(textRange: TextRange, message: String?, textAttributes: TextAttributesKey?) {
        HiglightingFactory.createInfoAnnotation(holder, textRange, message, textAttributes)
    }

    protected fun createInfoAnnotation(element: PsiElement, message: String? = null, textAttributes: TextAttributesKey) {
        createInfoAnnotation(element.textRange, message, textAttributes)
    }

    protected fun highlightName(element: PsiElement, attributesKey: TextAttributesKey, message: String? = null) {
        if (project.isNameHighlightingEnabled && !element.textRange.isEmpty) {
            createInfoAnnotation(element, message, attributesKey)
        }
    }

    protected fun highlightName(textRange: TextRange, attributesKey: TextAttributesKey, message: String? = null) {
        if (project.isNameHighlightingEnabled) {
            createInfoAnnotation(textRange, message, attributesKey)
        }
    }


    companion object {
        fun createHighlighters(holder: AnnotationHolder, project: Project): List<AfterResolveHighlighter> = listOf(
            TypeHighlighter(holder, project),
            FunctionCallHighlighter(holder, project),
            ExpressionsSmartcastHighlighter(holder, project),
            VariableReferenceHighlighter(holder, project),
        )
    }
}