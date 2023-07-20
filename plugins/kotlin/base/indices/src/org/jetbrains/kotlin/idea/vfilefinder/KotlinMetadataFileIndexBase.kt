// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.vfilefinder

import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInDefinitionFile
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.idea.base.indices.names.readKotlinMetadataDefinition
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.serialization.deserialization.MetadataPackageFragment
import org.jetbrains.kotlin.serialization.deserialization.getClassId

abstract class KotlinMetadataFileIndexBase(indexFunction: (ClassId) -> FqName) : KotlinFileIndexBase() {
    override fun getIndexer() = INDEXER

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(KotlinBuiltInFileType)

    override fun getVersion() = 2

    private val INDEXER = indexer { fileContent ->
        val builtIns = readKotlinMetadataDefinition(fileContent) as? BuiltInDefinitionFile ?: return@indexer null

        val singleClass = builtIns.proto.class_List.singleOrNull()
        if (singleClass != null) {
            return@indexer indexFunction(builtIns.nameResolver.getClassId(singleClass.fqName))
        }

        val facadeName = fileContent.fileName.substringBeforeLast(MetadataPackageFragment.DOT_METADATA_FILE_EXTENSION)
        val classId = ClassId(builtIns.packageFqName, Name.identifier(facadeName))
        indexFunction(classId)
    }
}