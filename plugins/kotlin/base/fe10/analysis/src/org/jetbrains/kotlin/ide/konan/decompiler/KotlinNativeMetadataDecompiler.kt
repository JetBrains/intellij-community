// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.ide.konan.decompiler

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.psi.fileTypes.KlibMetaFileType
import org.jetbrains.kotlin.idea.klib.Fe10KlibMetadataDecompiler
import org.jetbrains.kotlin.idea.klib.FileWithMetadata
import org.jetbrains.kotlin.idea.klib.KlibLoadingMetadataCache
import org.jetbrains.kotlin.library.metadata.KlibMetadataSerializerProtocol
import org.jetbrains.kotlin.library.metadata.KlibMetadataVersion
import org.jetbrains.kotlin.serialization.js.DynamicTypeDeserializer

class KotlinNativeMetadataDecompiler : Fe10KlibMetadataDecompiler<KlibMetadataVersion>(
    KlibMetaFileType,
    { KlibMetadataSerializerProtocol },
    DynamicTypeDeserializer,
    { KlibMetadataVersion.INSTANCE },
    { KlibMetadataVersion.INVALID_VERSION },
    KlibMetaFileType.STUB_VERSION
) {
    override fun doReadFile(file: VirtualFile): FileWithMetadata? {
        val fragment = KlibLoadingMetadataCache.getInstance().getCachedPackageFragment(file) ?: return null
        return FileWithMetadata.Compatible(fragment) //todo: check version compatibility
    }
}
