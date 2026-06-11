// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorageProvider

/**
 * KCRI provider that reads artifacts produced by Kotlin BTA directly from Gradle and Maven build dirs.
 *
 * For Gradle, the files are located under each module's build directory, in `build/kotlin/<compile task>/cacheable/cri/`.
 * For Maven, the files are located under each module's target directory, in `target/kotlin-ic/compile/cri/`.
 * Specific filenames and paths are provided by the [org.jetbrains.kotlin.buildtools.api.cri.CriToolchain].
 */
internal class BtaKotlinCompilerReferenceIndexStorageProvider : KotlinCompilerReferenceIndexStorageProvider {
    override fun isApplicable(project: Project): Boolean = BtaFileWatcher.isApplicable(project)

    override fun hasIndex(project: Project): Boolean = project.getCriPaths().any { it.hasLookupData() || it.hasSubtypeData() }

    override fun createStorage(project: Project, projectPath: String): KotlinCompilerReferenceIndexStorage? {
        if (project.isDisposed) return null
        val criRoots = project.getCriPaths()
        if (criRoots.isEmpty()) return null

        val lookupStoragesByRoot = createBtaStorageMap(
            criRoots = criRoots,
            createStorage = { BtaLookupInMemoryStorage.create(it, projectPath) },
        )
        val subtypeStoragesByRoot = createBtaStorageMap(
            criRoots = criRoots,
            createStorage = BtaSubtypeInMemoryStorage::create,
        )
        if (lookupStoragesByRoot.isEmpty()) return null

        return BtaKotlinCompilerReferenceIndexStorageImpl(project, projectPath, lookupStoragesByRoot, subtypeStoragesByRoot)
    }
}

/**
 * Checks if the receiver is the BTA implementation of [KotlinCompilerReferenceIndexStorageProvider]
 */
@TestOnly
@ApiStatus.Internal
fun KotlinCompilerReferenceIndexStorageProvider?.isBtaCriProvider(): Boolean = this is BtaKotlinCompilerReferenceIndexStorageProvider
