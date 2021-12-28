// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.highlighter.beforeResolve

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightingVisitor
import org.jetbrains.kotlin.idea.highlighter.BeforeResolveHighlightingExtension
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.idea.highlighter.NameHighlighter
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class AnnotationEntryHighlightingVisitor(
    holder: AnnotationHolder
) : AbstractBeforeResolveHiglightingVisitory(holder) {
    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        if (!NameHighlighter.namesHighlightingEnabled) return
        val range = annotationEntry.getTextRangeToHiglight() ?: return
        createInfoAnnotation(range, textAttributes = KotlinHighlightingColors.ANNOTATION)
    }

    private fun KtAnnotationEntry.getTextRangeToHiglight(): TextRange? {
        val atSymbol = atSymbol ?: return null
        val typeReference = typeReference ?: return null
        return TextRange(atSymbol.startOffset, typeReference.endOffset)
    }
}

class AnnotationsHighlightingExtension : BeforeResolveHighlightingExtension {
    override fun createVisitor(holder: AnnotationHolder): AbstractHighlightingVisitor =
        AnnotationEntryHighlightingVisitor(holder)
}