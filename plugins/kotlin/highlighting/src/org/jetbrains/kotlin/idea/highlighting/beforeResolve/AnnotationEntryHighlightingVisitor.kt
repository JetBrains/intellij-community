// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.highlighting.isNameHighlightingEnabled
import org.jetbrains.kotlin.idea.base.highlighting.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.idea.base.highlighting.BeforeResolveHighlightingExtension
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.idea.highlighting.beforeResolve.AbstractBeforeResolveHiglightingVisitory
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class AnnotationEntryHighlightingVisitor(
    holder: AnnotationHolder
) : AbstractBeforeResolveHiglightingVisitory(holder) {
    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        if (!annotationEntry.project.isNameHighlightingEnabled) return
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