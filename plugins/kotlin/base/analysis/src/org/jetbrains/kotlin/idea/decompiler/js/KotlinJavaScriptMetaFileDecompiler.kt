// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.decompiler.js

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinMetadataDecompiler
import org.jetbrains.kotlin.analysis.decompiler.stub.file.KotlinMetadataStubBuilder
import org.jetbrains.kotlin.idea.base.psi.fileTypes.KotlinJavaScriptMetaFileType
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.js.JsProtoBuf
import org.jetbrains.kotlin.psi.stubs.KotlinStubVersions
import org.jetbrains.kotlin.serialization.js.DynamicTypeDeserializer
import org.jetbrains.kotlin.serialization.js.JsSerializerProtocol
import org.jetbrains.kotlin.utils.JsMetadataVersion
import java.io.ByteArrayInputStream

class KotlinJavaScriptMetaFileDecompiler : KotlinMetadataDecompiler<JsMetadataVersion>(
    fileType = KotlinJavaScriptMetaFileType,
    serializerProtocol = { JsSerializerProtocol },
    flexibleTypeDeserializer = DynamicTypeDeserializer,
    expectedBinaryVersion = { JsMetadataVersion.INSTANCE },
    invalidBinaryVersion = { JsMetadataVersion.INVALID_VERSION },
    stubVersion = KotlinStubVersions.JS_STUB_VERSION
) {
    override fun readFile(bytes: ByteArray, file: VirtualFile): KotlinMetadataStubBuilder.FileWithMetadata {
        val stream = ByteArrayInputStream(bytes)

        val version = JsMetadataVersion.readFrom(stream)
        if (!version.isCompatible()) {
            return KotlinMetadataStubBuilder.FileWithMetadata.Incompatible(version)
        }

        JsProtoBuf.Header.parseDelimitedFrom(stream)

        val proto = ProtoBuf.PackageFragment.parseFrom(stream, JsSerializerProtocol.extensionRegistry)
        return KotlinMetadataStubBuilder.FileWithMetadata.Compatible(proto, version, JsSerializerProtocol)
    }
}
