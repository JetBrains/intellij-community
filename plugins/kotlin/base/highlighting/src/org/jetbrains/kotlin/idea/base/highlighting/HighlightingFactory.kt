// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.utils.addToStdlib.applyIf

object HighlightingFactory {
    fun addInfoAnnotation(holder: HighlightInfoHolder, textRange: TextRange, message: @NlsSafe String?, textAttributes: TextAttributesKey?) {
        holder.add(createInfoAnnotation(textRange, message, textAttributes).create())
    }
    fun createInfoAnnotation(textRange: TextRange, message: String?, textAttributes: TextAttributesKey?): HighlightInfo.Builder {
        val builder = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
        if (message != null) {
            builder.descriptionAndTooltip(message)
        }
        return builder
          .range(textRange)
          .applyIf(textAttributes != null) {
              textAttributes(textAttributes!!)
          }
    }
}