// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    override fun isApplicable(project: Project): Boolean = project.getCriPaths().isNotEmpty()

    override fun hasIndex(project: Project): Boolean = project.getCriPaths().any { it.hasLookupData() || it.hasSubtypeData() }

    override fun createStorage(project: Project, projectPath: String): KotlinCompilerReferenceIndexStorage? {
        val criRoots = project.getCriPaths()
        if (project.isDisposed || criRoots.isEmpty()) return null

        val lookupStorages = criRoots.mapNotNull { root ->
            BtaLookupInMemoryStorage.create(root, projectPath)
        }
        if (lookupStorages.isEmpty()) return null

        val subtypeStorages = criRoots.mapNotNull { root ->
            BtaSubtypeInMemoryStorage.create(root)
        }

        return BtaKotlinCompilerReferenceIndexStorageImpl(lookupStorages, subtypeStorages)
    }

    @OptIn(ExperimentalBuildToolsApi::class)
    private fun Project.getCriPaths(): List<Path> = ModuleManager.getInstance(this).modules
        .mapNotNull { it.getCriPath() }
        .filter { it.exists() }
}
