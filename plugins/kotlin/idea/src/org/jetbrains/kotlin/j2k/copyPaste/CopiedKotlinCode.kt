// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k.copyPaste

import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

import java.awt.datatransfer.DataFlavor

/**
 * A marker class for [ConvertTextJavaCopyPasteProcessor],
 * so that it doesn't try to run J2K on code copied from a Kotlin file.
 */
class CopiedKotlinCode : TextBlockTransferableData {
    override fun getFlavor(): DataFlavor = DATA_FLAVOR

    companion object {
        val DATA_FLAVOR: DataFlavor = DataFlavor(CopiedKotlinCode::class.java, KotlinBundle.message("copy.text.copied.kotlin.code"))
    }
}
