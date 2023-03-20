// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.cutPaste

import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import java.awt.datatransfer.DataFlavor

class MoveDeclarationsTransferableData(
    val sourceFileUrl: String,
    val sourceObjectFqName: String?,
    val declarationTexts: List<String>,
    val imports: List<String>
) : TextBlockTransferableData {

    override fun getFlavor() = DATA_FLAVOR

    companion object {
        val DATA_FLAVOR = DataFlavor(MoveDeclarationsCopyPasteProcessor::class.java, "class: MoveDeclarationsCopyPasteProcessor")

        val STUB_RENDERER = IdeDescriptorRenderers.SOURCE_CODE.withOptions {
            defaultParameterValueRenderer = { "xxx" } // we need default value to be parsed as expression
        }
    }
}