// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalBuildToolsApi::class)

package org.jetbrains.kotlin.idea.search.refIndex.jps

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_COMPILER_REFERENCE_INDEX_BUILDER_ID
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorageProvider
import org.jetbrains.kotlin.idea.search.refIndex.bta.BtaLookupInMemoryStorage
import org.jetbrains.kotlin.idea.search.refIndex.bta.BtaSubtypeInMemoryStorage
import org.jetbrains.kotlin.idea.search.refIndex.bta.createBtaStorageMap
import org.jetbrains.kotlin.idea.search.refIndex.bta.hasLookupData
import org.jetbrains.kotlin.idea.search.refIndex.bta.hasSubtypeData
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * KCRI provider that reads the CRI artifacts which the Build Tools API writes when JPS compiles through it.
 * The data sits in the `cri` directory of each module target's data root.
 */
internal class JpsBtaKotlinCompilerReferenceIndexStorageProvider : KotlinCompilerReferenceIndexStorageProvider {
    override fun isApplicable(project: Project): Boolean =
        KotlinArtifactsDownloader.isBuildToolsApiEnabled(KotlinJpsPluginSettings.jpsVersion(project)) && project.jpsCriRoots.any()

    override fun hasIndex(project: Project): Boolean = project.jpsCriRoots.any()

    override fun createStorage(project: Project, projectPath: String): KotlinCompilerReferenceIndexStorage? {
        if (project.isDisposed) return null
        val criRoots = project.jpsCriRoots.toList()
        if (criRoots.isEmpty()) return null

        val lookupStoragesByRoot = createBtaStorageMap(
            criRoots = criRoots,
            createStorage = { BtaLookupInMemoryStorage.create(it, projectPath) }
        )
        val subtypeStoragesByRoot = createBtaStorageMap(
            criRoots = criRoots,
            createStorage = BtaSubtypeInMemoryStorage::create
        )
        if (lookupStoragesByRoot.isEmpty()) return null

        return JpsBtaKotlinCompilerReferenceIndexStorageImpl(lookupStoragesByRoot, subtypeStoragesByRoot)
    }

    override val builderId: String
        get() = KOTLIN_COMPILER_REFERENCE_INDEX_BUILDER_ID
}

/**
 * The `cri` directories of the module targets that hold CRI data.
 * Each iteration scans the disk, listing the target data roots one target type at a time and checking each root.
 * A consumer that stops early skips the remaining checks and the later target types.
 */
private val Project.jpsCriRoots: Sequence<Path>
    get() = JavaModuleBuildTargetType.ALL_TYPES.asSequence()
        .map { buildTargetType -> buildDataPaths.getTargetTypeDataRootDir(buildTargetType) }
        .filter { buildTargetPath -> buildTargetPath.isDirectory() }
        .flatMap { buildTargetPath ->
            try {
                buildTargetPath.listDirectoryEntries().asSequence()
            } catch (_: IOException) {
                emptySequence()
            }
        }
        .map { targetDataRoot -> targetDataRoot.resolve(CriToolchain.DATA_PATH) }
        .filter { criRoot -> criRoot.hasLookupData() || criRoot.hasSubtypeData() }

/**
 * Checks if the receiver is the JPS BTA implementation of [KotlinCompilerReferenceIndexStorageProvider]
 */
@TestOnly
@ApiStatus.Internal
fun KotlinCompilerReferenceIndexStorageProvider?.isJpsBtaCriProvider(): Boolean = this is JpsBtaKotlinCompilerReferenceIndexStorageProvider
