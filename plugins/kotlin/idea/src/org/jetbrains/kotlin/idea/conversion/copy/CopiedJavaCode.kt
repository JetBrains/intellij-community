// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.conversion.copy

import com.intellij.codeInsight.editorActions.TextBlockTransferableData

import java.awt.datatransfer.DataFlavor

class CopiedJavaCode(val fileText: String, val startOffsets: IntArray, val endOffsets: IntArray) : TextBlockTransferableData {

    override fun getFlavor() = DATA_FLAVOR
    override fun getOffsetCount() = 0

    override fun getOffsets(offsets: IntArray?, index: Int) = index
    override fun setOffsets(offsets: IntArray?, index: Int) = index

    companion object {
        val DATA_FLAVOR: DataFlavor = DataFlavor(ConvertJavaCopyPasteProcessor::class.java, "class: ConvertJavaCopyPasteProcessor")
    }
}
