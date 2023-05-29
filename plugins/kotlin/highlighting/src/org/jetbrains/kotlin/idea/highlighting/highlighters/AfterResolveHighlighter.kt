// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.highlighting.HighlightingFactory
import org.jetbrains.kotlin.idea.base.highlighting.isNameHighlightingEnabled
import org.jetbrains.kotlin.psi.KtElement

internal abstract class AfterResolveHighlighter(protected val project: Project) {

    context(KtAnalysisSession)
    abstract fun highlight(element: KtElement) : List<HighlightInfo.Builder>

    private fun createInfoAnnotation(textRange: TextRange, message: String?, textAttributes: TextAttributesKey?): HighlightInfo.Builder {
        return HighlightingFactory.createInfoAnnotation(textRange, message, textAttributes)
    }

    protected fun createInfoAnnotation(element: PsiElement, message: String? = null, textAttributes: TextAttributesKey): HighlightInfo.Builder {
        return createInfoAnnotation(element.textRange, message, textAttributes)
    }

    protected fun highlightName(element: PsiElement, attributesKey: TextAttributesKey, message: String? = null): HighlightInfo.Builder? {
        return if (project.isNameHighlightingEnabled && !element.textRange.isEmpty) {
            createInfoAnnotation(element, message, attributesKey)
        } else {
            null
        }
    }

    protected fun highlightName(textRange: TextRange, attributesKey: TextAttributesKey, message: String? = null): HighlightInfo.Builder? {
        return if (project.isNameHighlightingEnabled) {
            createInfoAnnotation(textRange, message, attributesKey)
        } else {
            null
        }
    }


    companion object {
        fun createHighlighters(project: Project): List<AfterResolveHighlighter> = listOf(
          TypeHighlighter(project),
          FunctionCallHighlighter(project),
          ExpressionsSmartcastHighlighter(project),
          VariableReferenceHighlighter(project),
          DslHighlighter(project),
        )
    }
}