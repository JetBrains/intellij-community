// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.DetailedDescription
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

object HighlightingFactory {
    fun highlightName(element: PsiElement, highlightInfoType: HighlightInfoType, message: @DetailedDescription String? = null): HighlightInfo.Builder? {
        val project = element.project
        if (!element.textRange.isEmpty) {
            return highlightName(project, element, element.textRange, highlightInfoType, message)
        }
        return null
    }

    fun highlightName(
        project: Project,
        element: PsiElement,
        textRange: TextRange,
        highlightInfoType: HighlightInfoType,
        message: @DetailedDescription String? = null
    ): HighlightInfo.Builder {
        val elementTextRange = element.textRange
        assert(elementTextRange.contains(textRange)) {
            "Invalid textRange passed: $textRange. It must be within the $element text range ($elementTextRange)"
        }
        val builder = HighlightInfo.newHighlightInfo(highlightInfoType)
        if (message != null) {
            builder.descriptionAndTooltip(message)
        }
        val innerStart = textRange.startOffset - elementTextRange.startOffset
        val innerRange = TextRange.from(innerStart, textRange.length)
        val annotation = builder.range(element, innerRange)
        return annotation
    }
}