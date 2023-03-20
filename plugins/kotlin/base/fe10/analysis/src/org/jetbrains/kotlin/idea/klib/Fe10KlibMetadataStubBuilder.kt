// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.klib

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.decompiler.psi.text.DecompiledText
import org.jetbrains.kotlin.analysis.decompiler.psi.text.defaultDecompilerRendererOptions
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.serialization.SerializerExtensionProtocol
import org.jetbrains.kotlin.serialization.deserialization.FlexibleTypeDeserializer
import org.jetbrains.kotlin.serialization.js.DynamicTypeDeserializer

open class Fe10KlibMetadataStubBuilder(
    version: Int,
    fileType: FileType,
    serializerProtocol: () -> SerializerExtensionProtocol,
    readFile: (VirtualFile) -> FileWithMetadata?
) : KlibMetadataStubBuilder(version, fileType, serializerProtocol, readFile) {
    override fun getDecompiledText(
        file: FileWithMetadata.Compatible,
        serializerProtocol: SerializerExtensionProtocol,
        flexibleTypeDeserializer: FlexibleTypeDeserializer
    ): DecompiledText {
        val renderer = DescriptorRenderer.withOptions { defaultDecompilerRendererOptions() }
        return decompiledText(file, serializerProtocol, DynamicTypeDeserializer, renderer)
    }
}