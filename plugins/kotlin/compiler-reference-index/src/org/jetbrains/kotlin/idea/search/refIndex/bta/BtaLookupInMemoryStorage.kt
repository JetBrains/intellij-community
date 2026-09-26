// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain.Companion.cri
import org.jetbrains.kotlin.name.FqName
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes

@ApiStatus.Internal
class BtaLookupInMemoryStorage private constructor(
    // TODO KTIJ-37735: use persistent hash map to avoid retaining all CRI data in memory
    private val lookups: Map<Int, Set<Int>>,
    private val fileIdsToPaths: Map<Int, String>,
    projectPath: String,
) : BtaInMemoryStorage {
    private val baseDir = Path.of(projectPath)

    operator fun get(fqName: FqName): List<Path> {
        val hashCode = fqName.hashCode()
        val fileIds = lookups[hashCode] ?: return emptyList()
        return fileIds.mapNotNull { fileId ->
            fileIdsToPaths[fileId]?.let { baseDir.resolve(it).normalize() }
        }
    }

    @OptIn(ExperimentalBuildToolsApi::class)
    companion object {
        private val LOG = logger<BtaLookupInMemoryStorage>()

        fun create(criRoot: Path, projectPath: String): BtaLookupInMemoryStorage? {
            if (!criRoot.hasLookupData()) return null

            val lookupsData = try {
                criRoot.resolve(CriToolchain.LOOKUPS_FILENAME).readBytes()
            } catch (e: IOException) {
                LOG.warn("Failed to read CRI lookups data in $criRoot", e)
                return null
            }
            val fileIdsToPathsData = try {
                criRoot.resolve(CriToolchain.FILE_IDS_TO_PATHS_FILENAME).readBytes()
            } catch (e: IOException) {
                LOG.warn("Failed to read CRI file IDs to paths data in $criRoot", e)
                return null
            }

            val toolchains = try {
                KotlinToolchains.loadImplementation(BtaLookupInMemoryStorage::class.java.classLoader)
            } catch (e: IllegalStateException) {
                LOG.warn("Failed to load BTA toolchain implementation for lookups in $criRoot", e)
                return null
            }
            val (lookupEntries, fileIdToPathEntries) = toolchains.createBuildSession().use { session ->
                val criToolchain = session.kotlinToolchains.cri
                // TODO KTIJ-37735: use streaming deserialization to avoid reading whole files
                val lookupsOperation = criToolchain.createCriLookupDataDeserializationOperation(lookupsData)
                val fileIdsToPathsOperation = criToolchain.createCriFileIdToPathDataDeserializationOperation(fileIdsToPathsData)
                session.executeOperation(lookupsOperation) to session.executeOperation(fileIdsToPathsOperation)
            }

            val lookups = mutableMapOf<Int, MutableSet<Int>>()
            lookupEntries.forEach { entry ->
                val k = entry.fqNameHashCode ?: return@forEach
                lookups.getOrPut(k) { mutableSetOf() }.addAll(entry.fileIds)
            }
            val fileIdsToPaths = mutableMapOf<Int, String>()
            fileIdToPathEntries.forEach { entry ->
                val k = entry.fileId ?: return@forEach
                val v = entry.path ?: return@forEach
                fileIdsToPaths[k] = v
            }

            if (lookups.isEmpty() || fileIdsToPaths.isEmpty()) return null
            return BtaLookupInMemoryStorage(lookups, fileIdsToPaths, projectPath)
        }
    }

}

@OptIn(ExperimentalBuildToolsApi::class)
@ApiStatus.Internal
fun Path.hasLookupData(): Boolean = resolve(CriToolchain.LOOKUPS_FILENAME).exists() &&
        resolve(CriToolchain.FILE_IDS_TO_PATHS_FILENAME).exists()
