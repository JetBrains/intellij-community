// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.highlighting.beforeResolve

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.highlighting.BeforeResolveHighlightingExtension
import org.jetbrains.kotlin.idea.highlighter.visitor.AbstractHighlightingVisitor
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightInfoTypeSemanticNames
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class AnnotationEntryHighlightingVisitor(
    holder: HighlightInfoHolder
) : AbstractHighlightingVisitor(holder), DumbAware {
    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        val range = annotationEntry.getTextRangeToHighlight() ?: return
        highlightName(annotationEntry.project, range, KotlinHighlightInfoTypeSemanticNames.ANNOTATION)
    }

    private fun KtAnnotationEntry.getTextRangeToHighlight(): TextRange? {
        val atSymbol = atSymbol ?: return null
        val typeReference = typeReference ?: return null
        return TextRange(atSymbol.startOffset, typeReference.endOffset)
    }
}

class AnnotationsHighlightingExtension : BeforeResolveHighlightingExtension {
    override fun createVisitor(holder: HighlightInfoHolder): AbstractHighlightingVisitor =
        AnnotationEntryHighlightingVisitor(holder)
}