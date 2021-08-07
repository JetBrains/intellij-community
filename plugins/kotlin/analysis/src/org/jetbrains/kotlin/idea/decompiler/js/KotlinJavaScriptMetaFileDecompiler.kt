// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.decompiler.js

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.decompiler.common.FileWithMetadata
import org.jetbrains.kotlin.idea.decompiler.common.KotlinMetadataDecompiler
import org.jetbrains.kotlin.metadata.ProtoBuf
import org.jetbrains.kotlin.metadata.js.JsProtoBuf
import org.jetbrains.kotlin.psi.stubs.KotlinStubVersions
import org.jetbrains.kotlin.serialization.js.DynamicTypeDeserializer
import org.jetbrains.kotlin.serialization.js.JsSerializerProtocol
import org.jetbrains.kotlin.utils.JsMetadataVersion
import java.io.ByteArrayInputStream

class KotlinJavaScriptMetaFileDecompiler : KotlinMetadataDecompiler<JsMetadataVersion>(
    KotlinJavaScriptMetaFileType, { JsSerializerProtocol }, DynamicTypeDeserializer,
    { JsMetadataVersion.INSTANCE }, { JsMetadataVersion.INVALID_VERSION }, KotlinStubVersions.JS_STUB_VERSION
) {
    override fun readFile(bytes: ByteArray, file: VirtualFile): FileWithMetadata? {
        val stream = ByteArrayInputStream(bytes)

        val version = JsMetadataVersion.readFrom(stream)
        if (!version.isCompatible()) {
            return FileWithMetadata.Incompatible(version)
        }

        JsProtoBuf.Header.parseDelimitedFrom(stream)

        val proto = ProtoBuf.PackageFragment.parseFrom(stream, JsSerializerProtocol.extensionRegistry)
        return FileWithMetadata.Compatible(proto, version, JsSerializerProtocol)
    }
}
