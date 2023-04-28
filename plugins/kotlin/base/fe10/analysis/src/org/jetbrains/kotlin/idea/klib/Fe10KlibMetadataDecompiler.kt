// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.klib

import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.kotlin.analysis.decompiler.psi.text.DecompiledText
import org.jetbrains.kotlin.analysis.decompiler.psi.text.buildDecompiledText
import org.jetbrains.kotlin.analysis.decompiler.psi.text.defaultDecompilerRendererOptions
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.serialization.SerializerExtensionProtocol
import org.jetbrains.kotlin.serialization.deserialization.FlexibleTypeDeserializer
import org.jetbrains.kotlin.serialization.deserialization.getClassId
import org.jetbrains.kotlin.utils.addIfNotNull

abstract class Fe10KlibMetadataDecompiler<out V : BinaryVersion>(
    fileType: FileType,
    serializerProtocol: () -> SerializerExtensionProtocol,
    flexibleTypeDeserializer: FlexibleTypeDeserializer,
    expectedBinaryVersion: () -> V,
    invalidBinaryVersion: () -> V,
    stubVersion: Int
) : KlibMetadataDecompiler<V>(
    fileType,
    serializerProtocol,
    flexibleTypeDeserializer,
    expectedBinaryVersion,
    invalidBinaryVersion,
    stubVersion
) {
    private val renderer: DescriptorRenderer by lazy {
        DescriptorRenderer.withOptions { defaultDecompilerRendererOptions() }
    }

    override val metadataStubBuilder: KlibMetadataStubBuilder by lazy {
        Fe10KlibMetadataStubBuilder(stubVersion, fileType, serializerProtocol, ::readFileSafely)
    }

    override fun getDecompiledText(
        file: FileWithMetadata.Compatible,
        serializerProtocol: SerializerExtensionProtocol,
        flexibleTypeDeserializer: FlexibleTypeDeserializer
    ): DecompiledText {
        return decompiledText(
            file,
            serializerProtocol,
            flexibleTypeDeserializer,
            renderer
        )
    }
}

// This function is extracted for KotlinNativeMetadataStubBuilder, that's the difference from Big Kotlin.
internal fun decompiledText(
    file: FileWithMetadata.Compatible,
    serializerProtocol: SerializerExtensionProtocol,
    flexibleTypeDeserializer: FlexibleTypeDeserializer,
    renderer: DescriptorRenderer
): DecompiledText {
    val packageFqName = file.packageFqName
    val resolver = KlibMetadataDeserializerForDecompiler(
        packageFqName, file.proto, file.nameResolver,
        serializerProtocol, flexibleTypeDeserializer
    )
    val declarations = arrayListOf<DeclarationDescriptor>()
    declarations.addAll(resolver.resolveDeclarationsInFacade(packageFqName))
    for (classProto in file.classesToDecompile) {
        val classId = file.nameResolver.getClassId(classProto.fqName)
        declarations.addIfNotNull(resolver.resolveTopLevelClass(classId))
    }
    return buildDecompiledText(packageFqName, declarations, renderer)
}