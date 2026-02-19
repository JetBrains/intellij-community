// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.jps

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.jps.builders.storage.BuildDataPaths
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorageProvider
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

internal class JpsKotlinCompilerReferenceIndexStorageProvider : KotlinCompilerReferenceIndexStorageProvider {
    companion object {
        private val LOG = logger<JpsKotlinCompilerReferenceIndexStorageProvider>()
    }

    override fun isApplicable(project: Project): Boolean {
        val buildDataPaths = project.buildDataPaths
        return buildDataPaths.kotlinDataContainer != null
    }

    override fun hasIndex(project: Project): Boolean {
        return JpsLookupStorageReader.hasStorage(project)
    }

    override fun createStorage(project: Project, projectPath: String): KotlinCompilerReferenceIndexStorage? {
        if (project.isDisposed) return null
        val buildDataPaths = project.buildDataPaths
        val kotlinDataContainerPath = buildDataPaths.kotlinDataContainer ?: run {
            LOG.warn("${SettingConstants.KOTLIN_DATA_CONTAINER_ID} is not found")
            return null
        }

        val lookupStorageReader = JpsLookupStorageReader.create(kotlinDataContainerPath, projectPath) ?: run {
            LOG.warn("LookupStorage not found or corrupted")
            return null
        }
        val classOneToManyStorage = JpsClassOneToManyStorage(
            kotlinDataContainerPath.resolve(JpsKotlinCompilerReferenceIndexStorageImpl.SUBTYPES_STORAGE_NAME))


        val storage = JpsKotlinCompilerReferenceIndexStorageImpl(lookupStorageReader, classOneToManyStorage)
        if (!storage.initialize(buildDataPaths)) return null
        return storage
    }
}

internal val Project.buildDataPaths: BuildDataPaths
    get() = BuildDataPathsImpl(BuildManager.getInstance().getProjectSystemDir(this))

internal val BuildDataPaths.kotlinDataContainer: Path?
    get() = targetsDataRoot
        .resolve(SettingConstants.KOTLIN_DATA_CONTAINER_ID)
        .takeIf { it.exists() && it.isDirectory() }
        ?.listDirectoryEntries("${SettingConstants.KOTLIN_DATA_CONTAINER_ID}*")
        ?.firstOrNull()