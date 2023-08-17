// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.ID
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibLoadingMetadataCache
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.library.metadata.KlibMetadataProtoBuf
import org.jetbrains.kotlin.name.FqName

class KlibMetaFileIndex : KotlinFileIndexBase() {
    companion object {
        val NAME: ID<FqName, Void> = ID.create("org.jetbrains.kotlin.idea.vfilefinder.KlibMetaFileIndex")
    }

    override fun getName() = NAME

    override fun getIndexer() = INDEXER

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(KlibMetaFileType)

    override fun getVersion() = 4

    // This is to express intention to index all Kotlin/Native metadata files irrespectively to file size:
    override fun getFileTypesWithSizeLimitNotApplicable() = listOf(KlibMetaFileType)

    /*todo: check version?!*/
    private val INDEXER = indexer { fileContent ->
        val fragment = KlibLoadingMetadataCache
            .getInstance().getCachedPackageFragment(fileContent.file)
        if (fragment != null)
            FqName(fragment.getExtension(KlibMetadataProtoBuf.fqName))
        else
            null
    }
}