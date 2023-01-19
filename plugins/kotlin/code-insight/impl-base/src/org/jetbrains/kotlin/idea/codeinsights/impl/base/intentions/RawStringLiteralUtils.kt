// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.codeinsights.impl.base.convertToStringLiteral
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

fun convertToStringLiteral(element: KtStringTemplateExpression, editor: Editor?) {
    // Note that we must get the offset before replace element to a string literal
    // because after the conversion, element will be a dangling KtExpression.
    val startOffset = element.startOffset
    val endOffset = element.endOffset
    val currentOffset = editor?.caretModel?.currentCaret?.offset ?: startOffset

    val replaced = element.convertToStringLiteral()

    val offset = when {
        startOffset == currentOffset -> startOffset
        endOffset == currentOffset -> replaced.endOffset
        else -> minOf(currentOffset + 2, replaced.endOffset)
    }

    editor?.caretModel?.moveToOffset(offset)
}
