// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting.visitor

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AbstractAnnotationHolderHighlightingVisitor(private val holder: AnnotationHolder): AbstractHighlightingVisitor() {
    override fun createInfoAnnotation(textRange: TextRange, message: String?, textAttributes: TextAttributesKey?) {
        val builder = when (message) {
            null -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            else -> holder.newAnnotation(HighlightSeverity.INFORMATION, @Suppress("HardCodedStringLiteral") message)
        }

        return builder.apply {
            builder.range(textRange)

            if (textAttributes != null) {
                textAttributes(textAttributes)
            }
        }.create()
    }
}