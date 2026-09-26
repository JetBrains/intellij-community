// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE")

package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.util.application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.core.script.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.scriptingDebugLog
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal class ScriptArtifactsResolution(
    val classpath: List<File>,
    val messages: List<String>,
)

@Service(Service.Level.PROJECT)
internal class ScriptArtifactResolutionService(private val project: Project) {

    private val resolutions = ConcurrentHashMap<ScriptArtifactsRequest, ScriptArtifactsResolution>()
    private val locks = ConcurrentHashMap<ScriptArtifactsRequest, Mutex>()

    fun resolvedOrNull(request: ScriptArtifactsRequest): ScriptArtifactsResolution? = resolutions[request]

    suspend fun resolve(requests: List<ScriptArtifactsRequest>) {
        val pending = requests.filterNot { resolutions.containsKey(it) }
        if (pending.isEmpty()) {
            scriptingDebugLog { "download: everything already attempted, nothing to do" }
            return
        }

        scriptingDebugLog { "download: starting for ${pending.flatMap { it.artifacts }}" }
        reportSequentialProgress(pending.size) { reporter ->
            for (request in pending) {
                reporter.itemStep(
                    KotlinBaseScriptingBundle.message("progress.details.resolving.artifacts", request.artifacts.joinToString(", "))
                ) {
                    resolveSingle(request)
                }
            }
        }
        scriptingDebugLog { "download: finished" }
    }

    // Failures are recorded like successes, so one attempt per session is enough and a bad coordinate is not
    // re-fetched on every reload.
    private suspend fun resolveSingle(request: ScriptArtifactsRequest) {
        val lock = locks.computeIfAbsent(request) { Mutex() }
        lock.withLock {
            if (resolutions.containsKey(request)) return@withLock

            resolutions[request] = download(request)
            locks.remove(request, lock)
        }
    }

    private suspend fun download(request: ScriptArtifactsRequest): ScriptArtifactsResolution {
        request.unsupportedOptions.takeIf { it.isNotEmpty() }?.let { unsupported ->
            scriptingDebugLog { "download: ${request.artifacts} rejected, unsupported options ${unsupported.joinToString(", ")}" }
            return ScriptArtifactsResolution(
                classpath = emptyList(),
                messages = listOf(
                    KotlinBaseScriptingBundle.message("script.artifacts.unsupported.options", unsupported.joinToString(", "))
                ),
            )
        }

        application.service<ScriptRepositoryCredentials>().remember(request.repositories)
        val repositories = request.repositories.map { it.toDescription() }

        val messages = mutableListOf<String>()
        val classpath = mutableListOf<File>()

        for (coordinates in request.artifacts) {
            val properties = coordinates.toRepositoryLibraryProperties(request.isTransitive)
            if (properties == null) {
                scriptingDebugLog { "download: $coordinates rejected, coordinates the IDE cannot express" }
                messages += KotlinBaseScriptingBundle.message("script.artifacts.unsupported.coordinates", coordinates)
                continue
            }

            val roots = try {
                withContext(Dispatchers.IO) {
                    coroutineToIndicator { indicator ->
                        scriptingDebugLog { "download: fetching $coordinates transitive=${request.isTransitive} from ${repositories.map { it.url }.ifEmpty { listOf("<IDE-configured>") }}" }
                        JarRepositoryManager.loadDependenciesSync(
                            project, properties, false, false, null, repositories, indicator
                        )
                    }
                }
            } catch (e: CancellationException) {
                scriptingDebugLog { "download: cancelled while fetching $coordinates" }
                throw e
            } catch (e: Throwable) {
                scriptingDebugLog { "download: failed for $coordinates: ${e.message}" }
                messages += KotlinBaseScriptingBundle.message(
                    "script.artifacts.resolution.failed", coordinates, e.message ?: e.javaClass.name
                )
                continue
            }

            if (roots.isEmpty()) {
                scriptingDebugLog { "download: nothing found for $coordinates" }
                messages += KotlinBaseScriptingBundle.message("script.artifacts.not.found", coordinates)
                continue
            }

            val files = roots.toFiles()
            scriptingDebugLog { "download: $coordinates resolved to ${files.size} files" }
            classpath += files
        }

        return ScriptArtifactsResolution(classpath.distinct(), messages)
    }

    @TestOnly
    fun seedForTesting(request: ScriptArtifactsRequest, resolution: ScriptArtifactsResolution) {
        resolutions[request] = resolution
    }
}

private fun Collection<OrderRoot>.toFiles(): List<File> = map { VfsUtilCore.virtualToIoFile(it.file).canonicalFile }
