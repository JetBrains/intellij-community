// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.copyPaste

import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.util.TextRange
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import java.awt.datatransfer.DataFlavor

data class KotlinReferenceTransferableData(
    val sourceFileUrl: String,
    val sourceFileText: String,
    val sourceReferenceInfos: List<KotlinSourceReferenceInfo>,
    /**
     * The ranges of copied/cut text in the source file.
     */
    val sourceRanges: List<TextRange>,
    /**
     * The [FqName] of a non-local declaration containing selected texts, or `null` if no declarations fully include the selections.
     */
    val sourceLocation: FqName?,
) : TextBlockTransferableData {
    override fun getFlavor(): DataFlavor = dataFlavor

    companion object {
        val dataFlavor: DataFlavor by lazy {
            val dataClass = KotlinReferenceTransferableData::class.java

            DataFlavor(
                DataFlavor.javaJVMLocalObjectMimeType + ";class=" + dataClass.name,
                dataClass.simpleName,
                dataClass.classLoader,
            )
        }
    }
}

/**
 * Information about a reference collected at the moment of COPY/CUT.
 * @param rangeInTextToBePasted range in a text which will be used during PASTE, and which is a concatenation of all copied/cut selections
 */
data class KotlinSourceReferenceInfo(
    val rangeInSource: TextRange,
    val rangeInTextToBePasted: TextRange,
)

/**
 * Stores a copied/cut reference's element together with the corresponding pasted element,
 * which can be obtained during PASTE and before the formatting is applied.
 */
data class KotlinSourceReferenceInTargetFile(
    val sourceElement: KtElement,
    val targetElementPointer: SmartPsiElementPointer<KtElement>,
)

/**
 * Stores the result of resolving a copied/cut reference, which is required for restoring of the corresponding pasted reference.
 *
 * In the current implementation of [KotlinCopyPasteReferenceProcessor] references are resolved on a background thread,
 * after the text is pasted.
 */
data class KotlinResolvedSourceReference(
    val sourceElement: KtElement,
    val fqNames: List<FqName>,
    val isReferenceQualifiable: Boolean,
)