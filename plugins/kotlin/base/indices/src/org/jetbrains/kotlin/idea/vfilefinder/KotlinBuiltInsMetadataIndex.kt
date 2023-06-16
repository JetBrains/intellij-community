// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.ID
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInDefinitionFile
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltInsPackageFragmentProvider
import org.jetbrains.kotlin.idea.base.indices.names.readKotlinMetadataDefinition
import org.jetbrains.kotlin.name.FqName

class KotlinBuiltInsMetadataIndex : KotlinFileIndexBase() {
    companion object {
        val NAME: ID<FqName, Void> = ID.create("org.jetbrains.kotlin.idea.vfilefinder.KotlinBuiltInsMetadataIndex")
    }

    override fun getName() = NAME

    override fun getIndexer() = INDEXER

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(KotlinBuiltInFileType)

    override fun getVersion() = VERSION

    private val VERSION = 4

    private val INDEXER = indexer { fileContent ->
        val packageFqName =
            if (fileContent.fileType == KotlinBuiltInFileType &&
                fileContent.fileName.endsWith(JvmBuiltInsPackageFragmentProvider.DOT_BUILTINS_METADATA_FILE_EXTENSION)
            ) {
                val builtins = readKotlinMetadataDefinition(fileContent) as? BuiltInDefinitionFile
                builtins?.packageFqName
            } else null
        packageFqName
    }
}