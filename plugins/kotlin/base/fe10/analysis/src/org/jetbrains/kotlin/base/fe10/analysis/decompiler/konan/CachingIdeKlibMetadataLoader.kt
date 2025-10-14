// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fe10.analysis.decompiler.konan

import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibLoadingMetadataCache
import org.jetbrains.kotlin.konan.file.File as KFile
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.MetadataKotlinLibraryLayout
import org.jetbrains.kotlin.library.impl.KotlinLibraryImpl
import org.jetbrains.kotlin.library.metadata.CustomMetadataProtoLoader
import org.jetbrains.kotlin.library.metadata.KlibMetadataProtoBuf
import org.jetbrains.kotlin.metadata.ProtoBuf

internal object CachingIdeKlibMetadataLoader : CustomMetadataProtoLoader {
    override fun loadModuleHeader(library: KotlinLibrary): KlibMetadataProtoBuf.Header {
        val virtualFile = getVirtualFile(library, library.metadataLayout.moduleHeaderFile)
        return virtualFile?.let { cache.getCachedModuleHeader(virtualFile) } ?: KlibMetadataProtoBuf.Header.getDefaultInstance()
    }

    override fun loadPackageFragment(library: KotlinLibrary, packageFqName: String, partName: String): ProtoBuf.PackageFragment {
        val virtualFile = getVirtualFile(library, library.metadataLayout.packageFragmentFile(packageFqName, partName))
        return virtualFile?.let { cache.getCachedPackageFragment(virtualFile) } ?: ProtoBuf.PackageFragment.getDefaultInstance()
    }

    private fun getVirtualFile(library: KotlinLibrary, file: KFile): VirtualFile? =
        if (library.isZipped) asJarFileSystemFile(library.libraryFile, file) else asLocalFile(file)

    private fun asJarFileSystemFile(jarFile: KFile, localFile: KFile): VirtualFile? {
        val fullPath = jarFile.absolutePath + "!" + PathUtil.toSystemIndependentName(localFile.path)
        return StandardFileSystems.jar().findFileByPath(fullPath)
    }

    private fun asLocalFile(localFile: KFile): VirtualFile? {
        val fullPath = localFile.absolutePath
        return StandardFileSystems.local().findFileByPath(fullPath)
    }

    private val cache: KlibLoadingMetadataCache
        get() = KlibLoadingMetadataCache.getInstance()

    private val KotlinLibrary.isZipped: Boolean
        get() = (this as KotlinLibraryImpl).base.access.layout.isZipped

    private val KotlinLibrary.metadataLayout: MetadataKotlinLibraryLayout
        get() = (this as KotlinLibraryImpl).metadata.access.layout
}