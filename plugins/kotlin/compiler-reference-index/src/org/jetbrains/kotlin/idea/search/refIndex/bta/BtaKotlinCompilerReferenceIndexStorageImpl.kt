// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.search.refIndex.IncrementalKotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.name.FqName
import java.nio.file.Path

internal class BtaKotlinCompilerReferenceIndexStorageImpl(
    private val project: Project,
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
        // Rebuild from the current project roots so stale CRI directories
        // (which disappeared after a recompilation or a source set change) do not stay loaded indefinitely
        val updatedCriRoots = modules.flatMapTo(mutableSetOf(), Module::getCriPaths)
        val currentCriRoots = project.getCriPaths()

        val (refreshedLookupStorages, refreshedSubtypeStorages) = refreshBtaStorageMaps(
            currentCriRoots = currentCriRoots,
            updatedCriRoots = updatedCriRoots,
            lookupStoragesByRoot = lookupStoragesByRoot,
            subtypeStoragesByRoot = subtypeStoragesByRoot,
            createLookupStorage = { BtaLookupInMemoryStorage.create(it, projectPath) },
            createSubtypeStorage = BtaSubtypeInMemoryStorage::create,
        )

        lookupStoragesByRoot = refreshedLookupStorages
        subtypeStoragesByRoot = refreshedSubtypeStorages
        return refreshedLookupStorages.isNotEmpty()
    }

    override fun close() = Unit
}

fun refreshBtaStorageMaps(
    currentCriRoots: Collection<Path>,
    updatedCriRoots: Collection<Path>,
    lookupStoragesByRoot: Map<Path, BtaLookupInMemoryStorage>,
    subtypeStoragesByRoot: Map<Path, BtaSubtypeInMemoryStorage>,
    createLookupStorage: (Path) -> BtaLookupInMemoryStorage?,
    createSubtypeStorage: (Path) -> BtaSubtypeInMemoryStorage?,
): Pair<Map<Path, BtaLookupInMemoryStorage>, Map<Path, BtaSubtypeInMemoryStorage>> =
    refreshBtaStorageMap(
        currentCriRoots = currentCriRoots,
        updatedCriRoots = updatedCriRoots,
        storagesByRoot = lookupStoragesByRoot,
        createStorage = createLookupStorage,
    ) to refreshBtaStorageMap(
        currentCriRoots = currentCriRoots,
        updatedCriRoots = updatedCriRoots,
        storagesByRoot = subtypeStoragesByRoot,
        createStorage = createSubtypeStorage,
    )

private fun <T> refreshBtaStorageMap(
    currentCriRoots: Collection<Path>,
    updatedCriRoots: Collection<Path>,
    storagesByRoot: Map<Path, T>,
    createStorage: (Path) -> T?,
): Map<Path, T> = buildMap {
    for (criRoot in currentCriRoots) {
        val storage = if (criRoot in updatedCriRoots || criRoot !in storagesByRoot) {
            createStorage(criRoot)
        }
        else {
            storagesByRoot[criRoot]
        }
        if (storage != null) {
            put(criRoot, storage)
        }
    }
}
