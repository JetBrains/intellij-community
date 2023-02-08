// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
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
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.incremental.KOTLIN_CACHE_DIRECTORY_NAME
import org.jetbrains.kotlin.incremental.storage.BasicMapsOwner
import org.jetbrains.kotlin.incremental.storage.CollectionExternalizer
import org.jetbrains.kotlin.name.FqName
import java.nio.file.Path
import java.util.concurrent.Future
import kotlin.io.path.*
import kotlin.system.measureTimeMillis

class KotlinCompilerReferenceIndexStorage private constructor(
    kotlinDataContainerPath: Path,
    private val lookupStorageReader: LookupStorageReader,
) {
    companion object {
        /**
         * [org.jetbrains.kotlin.incremental.AbstractIncrementalCache.Companion.SUBTYPES]
         */
        private val SUBTYPES_STORAGE_NAME = "subtypes.${BasicMapsOwner.CACHE_EXTENSION}"

        private val STORAGE_INDEXING_EXECUTOR = AppExecutorUtil.createBoundedApplicationPoolExecutor(
            "Kotlin compiler references indexing", UnindexedFilesUpdater.getMaxNumberOfIndexingThreads()
        )

        private val LOG = logger<KotlinCompilerReferenceIndexStorage>()

        fun open(project: Project): KotlinCompilerReferenceIndexStorage? {
            val projectPath = runReadAction { project.takeUnless(Project::isDisposed)?.basePath } ?: return null
            val buildDataPaths = project.buildDataPaths
            val kotlinDataContainerPath = buildDataPaths?.kotlinDataContainer ?: kotlin.run {
                LOG.warn("${SettingConstants.KOTLIN_DATA_CONTAINER_ID} is not found")
                return null
            }

            val lookupStorageReader = LookupStorageReader.create(kotlinDataContainerPath, projectPath) ?: kotlin.run {
                LOG.warn("LookupStorage not found or corrupted")
                return null
            }

            val storage = KotlinCompilerReferenceIndexStorage(kotlinDataContainerPath, lookupStorageReader)
            if (!storage.initialize(buildDataPaths)) return null
            return storage
        }

        fun close(storage: KotlinCompilerReferenceIndexStorage?) {
            storage?.close().let {
                LOG.info("KCRI storage is closed" + if (it == null) " (didn't exist)" else "")
            }
        }

        fun hasIndex(project: Project): Boolean = LookupStorageReader.hasStorage(project)

        @TestOnly
        fun initializeForTests(
            buildDataPaths: BuildDataPaths,
            destination: ClassOneToManyStorage,
        ) = initializeSubtypeStorage(buildDataPaths, destination)

        internal val Project.buildDataPaths: BuildDataPaths?
            get() = BuildManager.getInstance().getProjectSystemDirectory(this)?.let(::BuildDataPathsImpl)

        internal val BuildDataPaths.kotlinDataContainer: Path?
            get() = targetsDataRoot?.toPath()
                ?.resolve(SettingConstants.KOTLIN_DATA_CONTAINER_ID)
                ?.takeIf { it.exists() && it.isDirectory() }
                ?.listDirectoryEntries("${SettingConstants.KOTLIN_DATA_CONTAINER_ID}*")
                ?.firstOrNull()

        private fun initializeSubtypeStorage(buildDataPaths: BuildDataPaths, destination: ClassOneToManyStorage): Boolean {
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

        private fun visitSubtypeStorages(buildDataPaths: BuildDataPaths, processor: (Path) -> Unit) {
            for (buildTargetType in JavaModuleBuildTargetType.ALL_TYPES) {
                val buildTargetPath = buildDataPaths.getTargetTypeDataRoot(buildTargetType).toPath()
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
    }

    private val subtypesStorage = ClassOneToManyStorage(kotlinDataContainerPath.resolve(SUBTYPES_STORAGE_NAME))

    /**
     * @return true if initialization was successful
     */
    private fun initialize(buildDataPaths: BuildDataPaths): Boolean = initializeSubtypeStorage(buildDataPaths, subtypesStorage)

    private fun close() {
        lookupStorageReader.close()
        subtypesStorage.closeAndClean()
    }

    fun getUsages(fqName: FqName): List<VirtualFile> = lookupStorageReader[fqName].mapNotNull { VfsUtil.findFile(it, false) }

    fun getSubtypesOf(fqName: FqName, deep: Boolean): Sequence<FqName> = subtypesStorage[fqName, deep]
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
