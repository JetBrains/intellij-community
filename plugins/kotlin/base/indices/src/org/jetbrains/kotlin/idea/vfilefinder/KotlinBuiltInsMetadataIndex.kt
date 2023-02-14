// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInDefinitionFile
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltInsPackageFragmentProvider
import org.jetbrains.kotlin.name.FqName

class KotlinBuiltInsMetadataIndex : KotlinFileIndexBase() {
    companion object {
        val NAME: ID<FqName, Void> = ID.create("org.jetbrains.kotlin.idea.vfilefinder.KotlinBuiltInsMetadataIndex")
    }

    override fun getName() = NAME

    override fun getIndexer() = INDEXER

    override fun getInputFilter() = FileBasedIndex.InputFilter { file -> FileTypeRegistry.getInstance().isFileOfType(file, KotlinBuiltInFileType) }

    override fun getVersion() = VERSION

    private val VERSION = 1

    private val INDEXER = indexer { fileContent ->
        if (fileContent.fileType == KotlinBuiltInFileType &&
            fileContent.fileName.endsWith(JvmBuiltInsPackageFragmentProvider.DOT_BUILTINS_METADATA_FILE_EXTENSION)) {
            val builtins = BuiltInDefinitionFile.read(fileContent.content, fileContent.file.parent)
            (builtins as? BuiltInDefinitionFile)?.packageFqName
        } else null
    }
}