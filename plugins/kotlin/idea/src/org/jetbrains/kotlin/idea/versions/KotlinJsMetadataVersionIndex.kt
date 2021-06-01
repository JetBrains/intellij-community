// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.versions

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import org.jetbrains.kotlin.js.JavaScript
import org.jetbrains.kotlin.utils.JsMetadataVersion
import org.jetbrains.kotlin.utils.KotlinJavascriptMetadata
import org.jetbrains.kotlin.utils.KotlinJavascriptMetadataUtils

object KotlinJsMetadataVersionIndex : KotlinMetadataVersionIndexBase<KotlinJsMetadataVersionIndex, JsMetadataVersion>(
    KotlinJsMetadataVersionIndex::class.java
) {
    override fun createBinaryVersion(versionArray: IntArray, extraBoolean: Boolean?): JsMetadataVersion =
        JsMetadataVersion(*versionArray)

    override fun getIndexer() = INDEXER

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return when (val fileType = FileTypeManager.getInstance().findFileTypeByName(JavaScript.NAME)) {
          null -> FileBasedIndex.InputFilter { file -> JavaScript.EXTENSION == file.extension }
          else -> DefaultFileTypeSpecificInputFilter(fileType)
        }
    }

    override fun getVersion() = VERSION

    private val VERSION = 3

    private val INDEXER = DataIndexer { inputData: FileContent ->
        val result = HashMap<JsMetadataVersion, Void?>()

        tryBlock(inputData) {
            val metadataList = ArrayList<KotlinJavascriptMetadata>()
            KotlinJavascriptMetadataUtils.parseMetadata(inputData.contentAsText, metadataList)
            for (metadata in metadataList) {
                val version = metadata.version.takeIf { it.isCompatible() }
                // Version is set to something weird
                    ?: JsMetadataVersion.INVALID_VERSION
                result[version] = null
            }
        }

        result
    }
}
