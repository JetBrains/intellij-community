// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInDefinitionFile
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.serialization.deserialization.MetadataPackageFragment
import org.jetbrains.kotlin.serialization.deserialization.getClassId

open class KotlinMetadataFileIndexBase<T>(classOfIndex: Class<T>, indexFunction: (ClassId) -> FqName) :
    KotlinFileIndexBase<T>(classOfIndex) {
    override fun getIndexer() = INDEXER

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(KotlinBuiltInFileType)

    override fun getVersion() = VERSION

    private val VERSION = 1

    private val INDEXER = indexer { fileContent ->
        if (fileContent.fileType == KotlinBuiltInFileType &&
            fileContent.fileName.endsWith(MetadataPackageFragment.DOT_METADATA_FILE_EXTENSION)
        ) {
            val builtins = BuiltInDefinitionFile.read(fileContent.content, fileContent.file)
            (builtins as? BuiltInDefinitionFile)?.let { builtinDefFile ->
                val proto = builtinDefFile.proto
                proto.class_List.singleOrNull()?.let { cls ->
                    indexFunction(builtinDefFile.nameResolver.getClassId(cls.fqName))
                } ?: indexFunction(
                    ClassId(
                        builtinDefFile.packageFqName,
                        Name.identifier(fileContent.fileName.substringBeforeLast(MetadataPackageFragment.DOT_METADATA_FILE_EXTENSION))
                    )
                )
            }
        } else null
    }
}