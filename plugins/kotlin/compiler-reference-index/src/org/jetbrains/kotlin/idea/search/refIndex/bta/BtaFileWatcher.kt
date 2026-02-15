// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.idea.base.util.isMavenModule
import org.jetbrains.kotlin.idea.gradle.configuration.readGradleProperty
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.io.IOException
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.getLastModifiedTime
import kotlin.time.Duration.Companion.seconds

/**
 * Watches KCRI artifact directories produced by Kotlin Build Tools API-based builds (Gradle and Maven)
 * and detects which modules were recompiled by comparing file timestamps.
 *
 * For Gradle, KCRI artifacts are located at `build/kotlin/compileKotlin/cacheable/cri/` under each module directory.
 * For Maven, KCRI artifacts are located at `target/kotlin-ic/compile/cri/` under each module directory.
 *
 * Uses periodic polling via a coroutine with [delay] to check KCRI artifact timestamps.
 */
@OptIn(ExperimentalBuildToolsApi::class)
internal class BtaFileWatcher(private val project: Project) {
    private val lastSeenCriTimestamps = ConcurrentHashMap<Path, FileTime>()

    fun watchIn(coroutineScope: CoroutineScope, onModulesCompiled: (List<Module>) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                delay(POLLING_INTERVAL)
                checkForExternalCompilation(onModulesCompiled)
            }
        }
    }

    private fun checkForExternalCompilation(onModulesCompiled: (List<Module>) -> Unit) {
        val upToDateModules = runReadAction {
            if (project.isDisposed) return@runReadAction emptyList()
            ModuleManager.getInstance(project).modules.filter { module ->
                val criPath = module.getCriPath() ?: return@filter false
                val currentTimestamp = try {
                    criPath.resolve(CriToolchain.LOOKUPS_FILENAME).getLastModifiedTime()
                } catch (e: IOException) {
                    LOG.warn("Failed to check CRI timestamp for lookups in module ${module.name}", e)
                    return@filter false
                }

                val previousTimestamp = lastSeenCriTimestamps[criPath]
                if (previousTimestamp == null || currentTimestamp > previousTimestamp) {
                    lastSeenCriTimestamps[criPath] = currentTimestamp
                    return@filter true
                }
                false
            }
        }

        if (upToDateModules.isNotEmpty()) {
            LOG.info("Detected CRI changes for ${upToDateModules.size} modules: ${upToDateModules.joinToString { it.name }}")
            onModulesCompiled(upToDateModules)
        }
    }

    companion object {
        private val LOG = logger<BtaFileWatcher>()
        private val POLLING_INTERVAL = 10.seconds
        private const val CRI_PROPERTY = "kotlin.compiler.generateCompilerRefIndex"

        internal fun isApplicable(project: Project): Boolean = isGradleCriEnabled(project) || isMavenCriEnabled(project)

        private fun isGradleCriEnabled(project: Project): Boolean = ReadAction.compute<Boolean, RuntimeException> {
            GradleSettings.getInstance(project).linkedProjectsSettings.isNotEmpty()
                    && readGradleProperty(project, CRI_PROPERTY)?.toBoolean() ?: false
        }

        // TODO KTIJ-37735: check if CRI_PROPERTY is enabled for Maven projects
        private fun isMavenCriEnabled(project: Project): Boolean = runReadAction {
            ModuleManager.getInstance(project).modules.any { it.isMavenModule }
        }
    }
}
