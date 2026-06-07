// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.idea.gradle.configuration.readGradleProperty
import org.jetbrains.kotlin.idea.search.refIndex.bta.BtaFileWatcher.Companion.ENABLE_BTA_CRI_KEY
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.IOException
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.time.Duration.Companion.seconds

/**
 * Watches KCRI artifact directories produced by Kotlin Build Tools API-based builds (Gradle and Maven)
 * and detects which modules were recompiled by comparing file timestamps.
 *
 * For Gradle, KCRI artifacts are located at `build/kotlin/<compile task>/cacheable/cri/` under each module directory.
 * For Maven, KCRI artifacts are located at `target/kotlin-ic/compile/cri/` under each module directory.
 *
 * Uses periodic polling via a coroutine with [delay] to check KCRI artifact timestamps.
 */
internal class BtaFileWatcher(private val project: Project) {
    private val lastSeenCriTimestamps = ConcurrentHashMap<Path, FileTime>()

    fun watchIn(coroutineScope: CoroutineScope, onModulesCompiled: (Collection<Module>) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                delay(POLLING_INTERVAL)
                try {
                    checkForExternalCompilation(onModulesCompiled)
                } catch (e: Exception) {
                    ensureActive()
                    LOG.warn("Error during CRI polling", e)
                }
            }
        }
    }

    private fun checkForExternalCompilation(onModulesCompiled: (Collection<Module>) -> Unit) {
        val modules = runReadActionBlocking {
            if (project.isDisposed) return@runReadActionBlocking emptyArray()
            ModuleManager.getInstance(project).modules
        }

        // Gradle import creates separate IntelliJ modules for `module_name`, `module_name.main`, and `module_name.test`,
        // but they share the same Gradle project directory and therefore the same CRI artifact paths.
        // Grouping by CRI path avoids checking the same directory multiple times per poll and reporting duplicate module updates.
        val modulesByCriPath = buildMap {
            for (module in modules) {
                for (criPath in module.getCriPaths()) {
                    getOrPut(criPath) { mutableSetOf() }.add(module)
                }
            }
        }

        val updatedModules = lastSeenCriTimestamps.computeUpdatedModules(
            modulesByPath = modulesByCriPath,
            getTimestamp = ::getCriArtifactTimestamp,
        )

        if (updatedModules.isNotEmpty()) {
            LOG.info("Detected CRI changes for ${updatedModules.size} modules: ${updatedModules.joinToString { it.name }}")
            onModulesCompiled(updatedModules)
        }
    }

    companion object {
        private val LOG = logger<BtaFileWatcher>()
        private val POLLING_INTERVAL = 10.seconds
        private const val ENABLE_BTA_CRI_KEY = "kotlin.cri.bta.support.enabled"

        /**
         * Maven/Gradle project property that enables Kotlin Compiler Reference Index artifact generation
         * by Kotlin BTA-based builds
         */
        private const val KOTLIN_CRI_GENERATION_PROPERTY = "kotlin.compiler.generateCompilerRefIndex"

        /**
         * Returns `true` when [ENABLE_BTA_CRI_KEY] is enabled and the project uses a BTA-based build system (Gradle or Maven)
         * with CRI generation enabled.
         */
        internal fun isApplicable(project: Project): Boolean =
            Registry.`is`(ENABLE_BTA_CRI_KEY) && (isGradleCriEnabled(project) || isMavenCriEnabled(project))

        private fun isGradleCriEnabled(project: Project): Boolean = runReadActionBlocking {
            GradleSettings.getInstance(project).linkedProjectsSettings.isNotEmpty()
                    && (readGradleProperty(project, KOTLIN_CRI_GENERATION_PROPERTY)?.toBoolean() ?: false)
        }

        private fun isMavenCriEnabled(project: Project): Boolean = runReadActionBlocking {
            MavenProjectsManager.getInstanceIfCreated(project)?.projects?.any { mavenProject ->
                mavenProject.properties.getProperty(KOTLIN_CRI_GENERATION_PROPERTY).toBoolean()
            } ?: false
        }

    }
}

@OptIn(ExperimentalBuildToolsApi::class)
@ApiStatus.Internal
fun getCriArtifactTimestamp(criPath: Path): FileTime? {
    // Get the latest timestamp across all CRI artifacts used by BTA storages.
    return sequenceOf(
        criPath.resolve(CriToolchain.LOOKUPS_FILENAME),
        criPath.resolve(CriToolchain.FILE_IDS_TO_PATHS_FILENAME),
        criPath.resolve(CriToolchain.SUBTYPES_FILENAME),
    )
        .filter { it.exists() }
        .mapNotNull {
            try {
                it.getLastModifiedTime()
            } catch (_: IOException) {
                null
            }
        }
        .maxOrNull()
}

/**
 * Returns modules whose path's [getTimestamp] is newer than the receiver's cached value.
 * Mutates the receiver: prunes paths absent from [modulesByPath] and stores advanced timestamps.
 */
@ApiStatus.Internal
fun <T : Any> ConcurrentMap<Path, FileTime>.computeUpdatedModules(
    modulesByPath: Map<Path, Set<T>>,
    getTimestamp: (Path) -> FileTime?,
): Set<T> {
    keys.retainAll(modulesByPath.keys)

    return buildSet {
        for ((path, modules) in modulesByPath) {
            val currentTimestamp = getTimestamp(path) ?: continue

            compute(path) { _, previousTimestamp ->
                if (previousTimestamp == null || currentTimestamp > previousTimestamp) {
                    addAll(modules)
                    currentTimestamp
                } else {
                    previousTimestamp
                }
            }
        }
    }
}
