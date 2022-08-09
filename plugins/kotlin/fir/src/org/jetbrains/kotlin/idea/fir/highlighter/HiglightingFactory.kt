// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.highlighter

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.util.applyIf

internal object HiglightingFactory {
    fun createInfoAnnotation(holder: AnnotationHolder, textRange: TextRange, message: String?, textAttributes: TextAttributesKey?) {
        val builder =
            if (message == null) holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            else holder.newAnnotation(HighlightSeverity.INFORMATION, message)
        builder
            .range(textRange)
            .applyIf(textAttributes != null) {
                textAttributes(textAttributes!!)
            }
            .create()
    }
}