// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.name.FqName

internal class BtaKotlinCompilerReferenceIndexStorageImpl(
    private val lookupStorages: List<BtaLookupInMemoryStorage>,
    private val subtypeStorages: List<BtaSubtypeInMemoryStorage>,
) : KotlinCompilerReferenceIndexStorage {

    private val lfs = LocalFileSystem.getInstance()

    override fun getUsages(fqName: FqName): List<VirtualFile> = lookupStorages
        .asSequence()
        .flatMap { it[fqName] }
        .distinct()
        .mapNotNull { lfs.findFileByNioFile(it) }
        .toList()

    override fun getSubtypesOf(fqName: FqName, deep: Boolean) = subtypeStorages
        .asSequence()
        .flatMap { it[fqName, deep] }
        .distinct()

    override fun close() {
        lookupStorages.forEach(BtaLookupInMemoryStorage::close)
        subtypeStorages.forEach(BtaSubtypeInMemoryStorage::close)
    }
}
