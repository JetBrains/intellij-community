// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.util.indexing.UnindexedFilesUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.search.refIndex.IncrementalKotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.name.FqName
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

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

        val refreshedLookupStorages = refreshBtaStorageMap(
            currentCriRoots = currentCriRoots,
            updatedCriRoots = updatedCriRoots,
            storagesByRoot = lookupStoragesByRoot,
            createStorage = { BtaLookupInMemoryStorage.create(it, projectPath) },
        )
        val refreshedSubtypeStorages = refreshBtaStorageMap(
            currentCriRoots = currentCriRoots,
            updatedCriRoots = updatedCriRoots,
            storagesByRoot = subtypeStoragesByRoot,
            createStorage = BtaSubtypeInMemoryStorage::create,
        )

        lookupStoragesByRoot = refreshedLookupStorages
        subtypeStoragesByRoot = refreshedSubtypeStorages
        return refreshedLookupStorages.isNotEmpty()
    }

    override fun close() = Unit
}

@VisibleForTesting
@ApiStatus.Internal
@Suppress("RAW_RUN_BLOCKING")
fun <T : BtaInMemoryStorage> refreshBtaStorageMap(
    currentCriRoots: Collection<Path>,
    updatedCriRoots: Collection<Path>,
    storagesByRoot: Map<Path, T>,
    createStorage: (Path) -> T?,
    parallelism: Int = getDefaultDeserializationParallelism(),
): Map<Path, T> {
    val updatedRootSet = updatedCriRoots.toSet()
    val refreshedStorages = ConcurrentHashMap<Path, T>()

    // TODO: remove once the whole storage API is async
    runBlocking {
        withContext(Dispatchers.IO.limitedParallelism(parallelism)) {
            currentCriRoots.forEachConcurrent(parallelism) { criRoot ->
                val storage = if (criRoot in updatedRootSet || criRoot !in storagesByRoot) {
                    createStorage(criRoot)
                } else {
                    storagesByRoot[criRoot]
                }
                if (storage != null) {
                    refreshedStorages[criRoot] = storage
                }
            }
        }
    }

    return buildMap(currentCriRoots.size) {
        for (criRoot in currentCriRoots) {
            refreshedStorages[criRoot]?.let { put(criRoot, it) }
        }
    }
}

@ApiStatus.Internal
fun <T : BtaInMemoryStorage> createBtaStorageMap(
    criRoots: Collection<Path>,
    createStorage: (Path) -> T?,
    parallelism: Int = getDefaultDeserializationParallelism(),
): Map<Path, T> = refreshBtaStorageMap(
    currentCriRoots = criRoots,
    updatedCriRoots = emptySet(),
    storagesByRoot = emptyMap(),
    createStorage = createStorage,
    parallelism = parallelism,
)

private fun getDefaultDeserializationParallelism(): Int {
    if (ApplicationManager.getApplication() == null) {
        return Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    }
    return UnindexedFilesUpdater.getNumberOfIndexingThreads()
}
