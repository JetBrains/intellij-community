// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.jps

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.idea.search.refIndex.IncrementalKotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.idea.search.refIndex.bta.BtaKotlinCompilerReferenceIndexStorageImpl
import org.jetbrains.kotlin.idea.search.refIndex.bta.BtaLookupInMemoryStorage
import org.jetbrains.kotlin.idea.search.refIndex.bta.BtaSubtypeInMemoryStorage
import org.jetbrains.kotlin.idea.search.refIndex.bta.getMergedSubtypes
import org.jetbrains.kotlin.idea.search.refIndex.bta.getMergedUsagePaths
import org.jetbrains.kotlin.name.FqName
import java.nio.file.Path

/**
 * KCRI storage over the CRI tables that JPS builds wrote through the Build Tools API.
 *
 * It does not implement [IncrementalKotlinCompilerReferenceIndexStorage]: that refresh would rebuild its root set
 * from the Gradle and Maven build directories. The service reopens the storage after each build instead.
 */
internal class JpsBtaKotlinCompilerReferenceIndexStorageImpl(
    private val lookupStoragesByRoot: Map<Path, BtaLookupInMemoryStorage>,
    private val subtypeStoragesByRoot: Map<Path, BtaSubtypeInMemoryStorage>
) : KotlinCompilerReferenceIndexStorage {
    private val fileManager = VirtualFileManager.getInstance()

    override fun getUsages(fqName: FqName): List<VirtualFile> =
        lookupStoragesByRoot.values.getMergedUsagePaths(fqName).mapNotNull { fileManager.findFileByNioPath(it) }

    override fun getSubtypesOf(fqName: FqName, deep: Boolean) = subtypeStoragesByRoot.values.getMergedSubtypes(fqName, deep)

    override fun close() = Unit
}
