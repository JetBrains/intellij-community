// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices.names

import com.intellij.util.indexing.FileContent
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltInDefinitionFile
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltInsPackageFragmentProvider
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmNameResolver
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.serialization.deserialization.MetadataPackageFragment
import org.jetbrains.kotlin.analysis.decompiler.stub.file.KotlinMetadataStubBuilder.FileWithMetadata.Compatible as CompatibleMetadata

private val ALLOWED_METADATA_EXTENSIONS = listOf(
    JvmBuiltInsPackageFragmentProvider.DOT_BUILTINS_METADATA_FILE_EXTENSION,
    MetadataPackageFragment.DOT_METADATA_FILE_EXTENSION
)

internal fun readKotlinMetadataDefinition(fileContent: FileContent): CompatibleMetadata? {
    if (fileContent.fileType != KotlinBuiltInFileType) {
        return null
    }

    val fileName = fileContent.fileName
    if (ALLOWED_METADATA_EXTENSIONS.none { fileName.endsWith(it) }) {
        return null
    }

    val definition = BuiltInDefinitionFile.read(fileContent.content, fileContent.file.parent) as? CompatibleMetadata ?: return null

    // '.kotlin_builtins' files sometimes appear in random libraries.
    // Below there's an additional check that the file is likely to be an actual part of built-ins.

    val nestingLevel = definition.packageFqName.pathSegments().size
    val rootPackageDirectory = generateSequence(fileContent.file) { it.parent }.drop(nestingLevel + 1).firstOrNull() ?: return null
    val metaInfDirectory = rootPackageDirectory.findChild("META-INF") ?: return null

    if (metaInfDirectory.children.none { it.extension == "kotlin_module" }) {
        // Here can be a more strict check.
        // For instance, we can check if the manifest file has a 'Kotlin-Runtime-Component' attribute.
        // It's unclear if it would break use-cases when the standard library is embedded, though.
        return null
    }

    return definition
}

internal fun readProtoPackageData(kotlinJvmBinaryClass: KotlinJvmBinaryClass): Pair<JvmNameResolver, ProtoBuf.Package>? {
    val header = kotlinJvmBinaryClass.classHeader
    val data = header.data ?: header.incompatibleData ?: return null
    val strings = header.strings ?: return null
    return JvmProtoBufUtil.readPackageDataFrom(data, strings)
}