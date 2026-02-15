// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPathOrNull
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorage
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorageProvider
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.exists

/**
 * KCRI provider that reads artifacts produced by Kotlin BTA directly from Gradle and Maven build dirs.
 *
 * For Gradle, the files are located under each module's build directory, in `build/kotlin/compileKotlin/cacheable/cri/`.
 * For Maven, the files are located under each module's target directory, in `target/kotlin-ic/compile/cri/`.
 * Specific filenames and paths are provided by the [org.jetbrains.kotlin.buildtools.api.cri.CriToolchain].
 */
internal class BtaKotlinCompilerReferenceIndexStorageProvider : KotlinCompilerReferenceIndexStorageProvider {
    companion object {
        private val LOG = logger<BtaKotlinCompilerReferenceIndexStorageProvider>()
    }

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
    private fun Project.getCriPaths(): List<Path> {
        return getGradleCriPaths() + getMavenCriPaths()
    }

    @OptIn(ExperimentalBuildToolsApi::class)
    private fun Project.getGradleCriPaths(): List<Path> {
        val modulePaths = getGradleSubmodulePaths()
        return modulePaths.mapNotNull { modulePath ->
            val buildDirPath = modulePath.toNioPathOrNull()?.resolve("build") ?: return@mapNotNull null
            val criPath = buildDirPath / "kotlin" / "compileKotlin" / "cacheable" / CriToolchain.DATA_PATH
            criPath.takeIf { it.exists() }
        }
    }

    @OptIn(ExperimentalBuildToolsApi::class)
    private fun Project.getMavenCriPaths(): List<Path> {
        val modulePaths = getMavenSubmodulePaths()
        return modulePaths.mapNotNull { modulePath ->
            val targetDirPath = modulePath.toNioPathOrNull()?.resolve("target") ?: return@mapNotNull null
            val criPath = targetDirPath / "kotlin-ic" / "compile" / CriToolchain.DATA_PATH
            criPath.takeIf { it.exists() }
        }
    }

    private fun Project.getGradleSubmodulePaths(): List<String> {
        val projectDataStorage = ExternalProjectsDataStorage.getInstance(this)
        return buildList {
            ExternalSystemManager.EP_NAME.forEachExtensionSafe { manager ->
                if (manager.systemId != GradleConstants.SYSTEM_ID) return@forEachExtensionSafe
                val submodulePaths = projectDataStorage
                    .list(manager.systemId)
                    .mapNotNull { it.externalProjectStructure }
                    .flatMap { root ->
                        ExternalSystemApiUtil.getChildren(root, ProjectKeys.MODULE)
                            .map { it.data.linkedExternalProjectPath }
                    }
                addAll(submodulePaths)
            }
        }
    }

    private fun Project.getMavenSubmodulePaths(): List<String> {
        val projectDataStorage = ExternalProjectsDataStorage.getInstance(this)
        return buildList {
            ExternalSystemManager.EP_NAME.forEachExtensionSafe { manager ->
                if (manager.systemId != MavenUtil.SYSTEM_ID) return@forEachExtensionSafe
                val submodulePaths = projectDataStorage
                    .list(manager.systemId)
                    .mapNotNull { it.externalProjectStructure }
                    .flatMap { root ->
                        ExternalSystemApiUtil.getChildren(root, ProjectKeys.MODULE)
                            .map { it.data.linkedExternalProjectPath }
                    }
                addAll(submodulePaths)
            }
        }
    }
}
