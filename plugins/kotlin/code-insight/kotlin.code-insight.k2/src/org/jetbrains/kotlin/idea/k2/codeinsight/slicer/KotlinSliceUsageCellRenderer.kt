// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.slicer

import com.intellij.slicer.SliceUsage
import com.intellij.slicer.SliceUsageCellRendererBase

object KotlinSliceUsageCellRenderer : SliceUsageCellRendererBase() {
    override fun customizeCellRendererFor(sliceUsage: SliceUsage) {
        if (sliceUsage !is KotlinSliceUsage) return
        for (textChunk in sliceUsage.getText()) {
            append(textChunk.text, textChunk.simpleAttributesIgnoreBackground)
        }
    }
}
