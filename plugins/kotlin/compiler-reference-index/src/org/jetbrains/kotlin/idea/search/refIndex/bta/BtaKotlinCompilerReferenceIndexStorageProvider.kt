// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorageProvider
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * KCRI provider that reads artifacts produced by Kotlin BTA directly from Gradle and Maven build dirs.
 *
 * For Gradle, the files are located under each module's build directory, in `build/kotlin/compileKotlin/cacheable/cri/`.
 * For Maven, the files are located under each module's target directory, in `target/kotlin-ic/compile/cri/`.
 * Specific filenames and paths are provided by the [org.jetbrains.kotlin.buildtools.api.cri.CriToolchain].
 */
internal class BtaKotlinCompilerReferenceIndexStorageProvider : KotlinCompilerReferenceIndexStorageProvider {
    override fun isApplicable(project: Project): Boolean = BtaFileWatcher.isApplicable(project)

    override fun hasIndex(project: Project): Boolean = project.getCriPaths().any { it.hasLookupData() || it.hasSubtypeData() }

    override fun createStorage(project: Project, projectPath: String): KotlinCompilerReferenceIndexStorage? {
        val criRoots = project.getCriPaths()
        if (project.isDisposed || criRoots.isEmpty()) return null

        val lookupStoragesByRoot = buildMap {
            for (root in criRoots) {
                val storage = BtaLookupInMemoryStorage.create(root, projectPath) ?: continue
                put(root, storage)
            }
        }
        if (lookupStoragesByRoot.isEmpty()) return null

        val subtypeStoragesByRoot = buildMap {
            for (root in criRoots) {
                val storage = BtaSubtypeInMemoryStorage.create(root) ?: continue
                put(root, storage)
            }
        }

        return BtaKotlinCompilerReferenceIndexStorageImpl(projectPath, lookupStoragesByRoot, subtypeStoragesByRoot)
    }

    @OptIn(ExperimentalBuildToolsApi::class)
    private fun Project.getCriPaths(): List<Path> = ModuleManager.getInstance(this).modules
        .mapNotNull { it.getCriPath() }
        .distinct()
        .filter { it.exists() }
}
