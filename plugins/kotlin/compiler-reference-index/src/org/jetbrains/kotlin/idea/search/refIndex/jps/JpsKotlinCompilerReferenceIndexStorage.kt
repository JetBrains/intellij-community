// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.jps

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.indexing.UnindexedFilesUpdater
import com.intellij.util.io.CorruptedException
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.PersistentHashMap
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.incremental.KOTLIN_CACHE_DIRECTORY_NAME
import org.jetbrains.kotlin.incremental.storage.BasicMapsOwner
import org.jetbrains.kotlin.incremental.storage.CollectionExternalizer
import org.jetbrains.kotlin.name.FqName
import java.nio.file.Path
import java.util.concurrent.Future
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists
import kotlin.system.measureTimeMillis

internal class JpsKotlinCompilerReferenceIndexStorageImpl(
    private val lookupStorageReader: JpsLookupStorageReader,
    private val subtypesStorage: JpsClassOneToManyStorage,
) : KotlinCompilerReferenceIndexStorage {
    companion object {
        /**
         * [org.jetbrains.kotlin.incremental.AbstractIncrementalCache.SUBTYPES]
         */
        internal val SUBTYPES_STORAGE_NAME = "subtypes.${BasicMapsOwner.CACHE_EXTENSION}"

        private val STORAGE_INDEXING_EXECUTOR = AppExecutorUtil.createBoundedApplicationPoolExecutor(
            "Kotlin compiler references indexing", UnindexedFilesUpdater.getMaxNumberOfIndexingThreads()
        )

        private val LOG = logger<JpsKotlinCompilerReferenceIndexStorageImpl>()
    }

    override fun getUsages(fqName: FqName): List<VirtualFile> =
        lookupStorageReader[fqName].mapNotNull { VfsUtil.findFile(it, false) }

    override fun getSubtypesOf(fqName: FqName, deep: Boolean): Sequence<FqName> =
        subtypesStorage[fqName, deep]

    override fun close() {
        lookupStorageReader.close()
        subtypesStorage.closeAndClean()
    }

    private fun visitSubtypeStorages(buildDataPaths: BuildDataPaths, processor: (Path) -> Unit) {
        for (buildTargetType in JavaModuleBuildTargetType.ALL_TYPES) {
            val buildTargetPath = buildDataPaths.getTargetTypeDataRootDir(buildTargetType)
            if (buildTargetPath.notExists() || !buildTargetPath.isDirectory()) continue
            buildTargetPath.forEachDirectoryEntry { targetDataRoot ->
                val workingPath = targetDataRoot.takeIf { it.isDirectory() }
                    ?.resolve(KOTLIN_CACHE_DIRECTORY_NAME)
                    ?.resolve(SUBTYPES_STORAGE_NAME)
                    ?.takeUnless { it.notExists() }
                    ?: return@forEachDirectoryEntry

                processor(workingPath)
            }
        }
    }

    private fun initializeSubtypeStorage(buildDataPaths: BuildDataPaths, destination: JpsClassOneToManyStorage): Boolean {
        var wasCorrupted = false
        val destinationMap = MultiMap.createConcurrentSet<String, String>()

        val futures = mutableListOf<Future<*>>()
        val timeOfFilling = measureTimeMillis {
            visitSubtypeStorages(buildDataPaths) { storagePath ->
                futures += STORAGE_INDEXING_EXECUTOR.submit {
                    try {
                        initializeStorage(destinationMap, storagePath)
                    } catch (e: CorruptedException) {
                        wasCorrupted = true
                        LOG.warn("KCRI storage was corrupted", e)
                    }
                }
            }

            try {
                for (future in futures) {
                    future.get()
                }
            } catch (e: InterruptedException) {
                LOG.warn("KCRI initialization was interrupted")
                throw e
            }
        }

        if (wasCorrupted) return false

        val timeOfFlush = measureTimeMillis {
            for ((key, values) in destinationMap.entrySet()) {
                destination.put(key, values)
            }
        }

        LOG.info("KCRI storage is opened: took ${timeOfFilling + timeOfFlush} ms for ${futures.size} storages (filling map: $timeOfFilling ms, flush to storage: $timeOfFlush ms)")
        return true
    }

    @TestOnly
    fun initializeForTests(
        buildDataPaths: BuildDataPaths,
        destination: JpsClassOneToManyStorage,
    ) = initializeSubtypeStorage(buildDataPaths, destination)

    /**
     * @return true if initialization was successful
     */
    internal fun initialize(buildDataPaths: BuildDataPaths): Boolean =
        initializeSubtypeStorage(buildDataPaths, subtypesStorage)
}

private fun initializeStorage(destinationMap: MultiMap<String, String>, subtypesSourcePath: Path) {
    createKotlinDataReader(subtypesSourcePath).use { source ->
        source.processKeys { key ->
            source[key]?.let { values ->
                destinationMap.putValues(key, values)
            }

            true
        }
    }
}

private fun createKotlinDataReader(storagePath: Path): PersistentHashMap<String, Collection<String>> = openReadOnlyPersistentHashMap(
    storagePath,
    EnumeratorStringDescriptor.INSTANCE,
    CollectionExternalizer<String>(EnumeratorStringDescriptor.INSTANCE, ::SmartList),
)
