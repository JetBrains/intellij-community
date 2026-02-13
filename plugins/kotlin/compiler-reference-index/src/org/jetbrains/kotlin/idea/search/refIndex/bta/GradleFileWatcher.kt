// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.cri.CriToolchain
import org.jetbrains.kotlin.idea.gradle.configuration.readGradleProperty
import org.jetbrains.plugins.gradle.settings.GradleSettings.getInstance
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Watches Compiler Reference Index artifact directories produced by Gradle builds
 * and detects which modules were recompiled by comparing file timestamps.
 *
 * Uses periodic polling via a coroutine with [delay] to check CRI artifact timestamps.
 */
@OptIn(ExperimentalBuildToolsApi::class)
internal class GradleFileWatcher(
    private val project: Project,
    coroutineScope: CoroutineScope,
    private val onModulesCompiled: (List<Module>) -> Unit,
) {

    private val lastSeenCriTimestamps = ConcurrentHashMap<Path, FileTime>()

    init {
        coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                delay(POLLING_INTERVAL)
                checkForExternalCompilation()
            }
        }
    }

    private fun checkForExternalCompilation() {
        val modulesAndPaths = runReadAction {
            val project = project.takeUnless { it.isDisposed } ?: return@runReadAction null
            ModuleManager.getInstance(project).modules.mapNotNull { module ->
                val externalPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return@mapNotNull null
                val criPath = Path.of(externalPath, "build", "kotlin", "compileKotlin", "cacheable", CriToolchain.DATA_PATH)
                module to criPath
            }.distinct()
        } ?: return

        val compiledModules = mutableListOf<Module>()
        for ((module, criPath) in modulesAndPaths) {
            val lookupsFile = criPath.resolve(CriToolchain.LOOKUPS_FILENAME)
            val currentTimestamp = try {
                Files.getLastModifiedTime(lookupsFile)
            } catch (_: Exception) {
                continue
            }

            val previousTimestamp = lastSeenCriTimestamps[criPath]
            if (previousTimestamp == null || currentTimestamp > previousTimestamp) {
                compiledModules.add(module)
                lastSeenCriTimestamps[criPath] = currentTimestamp
            }
        }

        if (compiledModules.isNotEmpty()) {
            LOG.info("Detected CRI changes for ${compiledModules.size} modules: ${compiledModules.joinToString { it.name }}")
            onModulesCompiled(compiledModules)
        }
    }

    companion object {
        private val LOG = logger<GradleFileWatcher>()
        private val POLLING_INTERVAL = 10.seconds
        private const val CRI_GRADLE_PROPERTY = "kotlin.compiler.generateCompilerRefIndex"

        internal fun isApplicable(project: Project): Boolean = getInstance(project).linkedProjectsSettings.isNotEmpty() &&
                readGradleProperty(project, CRI_GRADLE_PROPERTY)?.toBoolean() ?: false
    }
}
