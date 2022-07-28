// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.highlighter.beforeResolve

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.fir.highlighter.HiglightingFactory
import org.jetbrains.kotlin.idea.base.highlighting.visitor.AbstractHighlightingVisitor

abstract class AbstractBeforeResolveHiglightingVisitory(protected val holder: AnnotationHolder): AbstractHighlightingVisitor() {
    override fun createInfoAnnotation(textRange: TextRange, message: String?, textAttributes: TextAttributesKey?) {
        HiglightingFactory.createInfoAnnotation(holder, textRange, message, textAttributes)
    }
}