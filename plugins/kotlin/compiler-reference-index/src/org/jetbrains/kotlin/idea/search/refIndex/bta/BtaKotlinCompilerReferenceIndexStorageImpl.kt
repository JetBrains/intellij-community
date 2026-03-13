// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.search.refIndex.IncrementalKotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.name.FqName
import java.nio.file.Path

internal class BtaKotlinCompilerReferenceIndexStorageImpl(
    private val projectPath: String,
    @Volatile private var lookupStoragesByRoot: Map<Path, BtaLookupInMemoryStorage>,
    @Volatile private var subtypeStoragesByRoot: Map<Path, BtaSubtypeInMemoryStorage>,
) : KotlinCompilerReferenceIndexStorage, IncrementalKotlinCompilerReferenceIndexStorage {

    private val lfs = LocalFileSystem.getInstance()

    override fun getUsages(fqName: FqName): List<VirtualFile> = lookupStoragesByRoot.values
        .asSequence()
        .flatMap { it[fqName] }
        .distinct()
        .mapNotNull { lfs.findFileByNioFile(it) }
        .toList()

    override fun getSubtypesOf(fqName: FqName, deep: Boolean) = subtypeStoragesByRoot.values
        .asSequence()
        .flatMap { it[fqName, deep] }
        .distinct()

    override fun refreshModules(modules: Collection<Module>): Boolean {
        val updatedCriRoots = modules.mapNotNullTo(mutableSetOf(), Module::getCriPath)
        if (updatedCriRoots.isEmpty()) return lookupStoragesByRoot.isNotEmpty()

        val refreshedLookupStorages = lookupStoragesByRoot.toMutableMap()
        val refreshedSubtypeStorages = subtypeStoragesByRoot.toMutableMap()

        for (criRoot in updatedCriRoots) {
            val lookupStorage = BtaLookupInMemoryStorage.create(criRoot, projectPath)
            if (lookupStorage != null) {
                refreshedLookupStorages[criRoot] = lookupStorage
            } else {
                refreshedLookupStorages.remove(criRoot)
            }

            val subtypeStorage = BtaSubtypeInMemoryStorage.create(criRoot)
            if (subtypeStorage != null) {
                refreshedSubtypeStorages[criRoot] = subtypeStorage
            } else {
                refreshedSubtypeStorages.remove(criRoot)
            }
        }

        lookupStoragesByRoot = refreshedLookupStorages
        subtypeStoragesByRoot = refreshedSubtypeStorages
        return refreshedLookupStorages.isNotEmpty()
    }

    override fun close() = Unit
}
