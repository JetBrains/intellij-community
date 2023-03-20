// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.util.TextRange
import java.awt.datatransfer.DataFlavor
import java.io.Serializable

class BasicKotlinReferenceTransferableData(
    val sourceFileUrl: String,
    val packageName: String,
    val imports: List<String>,
    val sourceTextOffset: Int,
    val sourceText: String,
    val textRanges: List<TextRange>,
    // references are empty if there are no any references
    // null when references have to be calculated on paste phase
    val references: List<KotlinReferenceData>?,
    val locationFqName: String?
) : TextBlockTransferableData, Cloneable, Serializable {
    override fun getFlavor() = dataFlavor

    public override fun clone(): BasicKotlinReferenceTransferableData {
        try {
            return super.clone() as BasicKotlinReferenceTransferableData
        } catch (e: CloneNotSupportedException) {
            throw RuntimeException()
        }
    }

    companion object {
        val dataFlavor: DataFlavor? by lazy {
            try {
                val dataClass = BasicKotlinReferenceTransferableData::class.java
                DataFlavor(
                    DataFlavor.javaJVMLocalObjectMimeType + ";class=" + dataClass.name,
                    "BasicKotlinReferenceTransferableData",
                    dataClass.classLoader
                )
            } catch (e: NoClassDefFoundError) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

}

