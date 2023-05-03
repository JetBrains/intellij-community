// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange

object HighlightingFactory {
    fun createInfoAnnotation(holder: HighlightInfoHolder, textRange: TextRange, message: String?, textAttributes: TextAttributesKey?) {
        val info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
        if (message != null) info.descriptionAndTooltip(message)
        info.range(textRange)

        if (textAttributes != null) {
            info.textAttributes(textAttributes)
        }
        holder.add(info.create())
    }
}