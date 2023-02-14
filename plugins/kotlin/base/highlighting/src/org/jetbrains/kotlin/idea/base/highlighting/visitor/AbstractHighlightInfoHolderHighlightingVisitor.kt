// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting.visitor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AbstractHighlightInfoHolderHighlightingVisitor(private val holder: HighlightInfoHolder) : AbstractHighlightingVisitor() {
    override fun createInfoAnnotation(textRange: TextRange, message: String?, textAttributes: TextAttributesKey?) {
        val annotation = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).apply {
            range(textRange)

            if (message != null) {
                @Suppress("HardCodedStringLiteral")
                descriptionAndTooltip(message)
            }

            if (textAttributes != null) {
                textAttributes(textAttributes)
            }
        }.create()

        holder.add(annotation)
    }
}