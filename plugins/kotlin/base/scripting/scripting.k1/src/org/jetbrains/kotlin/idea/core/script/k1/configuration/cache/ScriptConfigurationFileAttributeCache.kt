// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1.configuration.cache

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.shared.CachedConfigurationInputs
import org.jetbrains.kotlin.idea.core.script.k1.configuration.loader.ScriptConfigurationLoader
import org.jetbrains.kotlin.idea.core.script.k1.configuration.loader.ScriptConfigurationLoadingContext
import org.jetbrains.kotlin.idea.core.script.v1.AbstractFileGistService
import org.jetbrains.kotlin.idea.core.script.v1.readObject
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.v1.writeObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper.FromCompilationConfiguration
import java.io.DataInput
import java.io.DataOutput
import java.io.Serializable
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.withDefaultsFrom
import kotlin.script.experimental.jvm.impl.toClassPathOrEmpty

internal class ScriptConfigurationFileAttributeCache(
    val project: Project
) : ScriptConfigurationLoader {
    /**
     * todo(KT-34444): this should be changed to storing all roots in the persistent file cache
     */
    override fun loadDependencies(
        isFirstLoad: Boolean,
        ktFile: KtFile,
        scriptDefinition: ScriptDefinition,
        context: ScriptConfigurationLoadingContext
    ): Boolean {
        if (!isFirstLoad) return false

        val virtualFile = ktFile.originalFile.virtualFile
        val fromFs = load(virtualFile) ?: return false

        context.saveNewConfiguration(
            virtualFile,
            ScriptConfigurationSnapshot(
                fromFs.inputs,
                fromFs.reports,
                FromCompilationConfiguration(
                    KtFileScriptSource(ktFile),
                    fromFs.configuration.with {
                        // should be updated from definition because host configuration is transient
                        hostConfiguration.update { it.withDefaultsFrom(scriptDefinition.hostConfiguration) }
                    }
                )
            )
        )
        return fromFs.inputs.isUpToDate(ktFile.project, virtualFile, ktFile)
    }

    private fun load(
        virtualFile: VirtualFile
    ): ScriptConfigurationSnapshotForFS? {
        val configurationSnapshot = ScriptConfigurationSnapshotFile[project, virtualFile] ?: return null
        scriptingDebugLog(virtualFile) { "configuration from fileAttributes = $configurationSnapshot" }

        val configuration = configurationSnapshot.configuration ?: return null

        if (!areDependenciesValid(virtualFile, configuration)) {
            save(virtualFile, null)
            return null
        }

        return configurationSnapshot
    }

    private fun areDependenciesValid(file: VirtualFile, configuration: ScriptCompilationConfiguration): Boolean =
        configuration[ScriptCompilationConfiguration.dependencies].toClassPathOrEmpty().all {
            val path = it.toPath()
            if (path.exists()) {
                true
            } else {
                scriptingDebugLog(file) {
                    "classpath root saved to file attribute doesn't exist: ${path.pathString}"
                }
                false
            }
        }

    fun save(file: VirtualFile, value: ScriptConfigurationSnapshot?) {
        ScriptConfigurationSnapshotFile[project, file] = value?.let {
            ScriptConfigurationSnapshotForFS(
                it.inputs,
                it.reports.filter { report -> report.exception == null /* exceptions deserialization might later result in ClassNotFoundError */},
                it.configuration?.configuration
            )
        }
    }
}

internal class ScriptConfigurationSnapshotForFS(
    val inputs: CachedConfigurationInputs,
    val reports: List<ScriptDiagnostic>,
    val configuration: ScriptCompilationConfiguration?
) : Serializable

@Service(Service.Level.PROJECT)
internal class ScriptConfigurationSnapshotFile : AbstractFileGistService<ScriptConfigurationSnapshotForFS>(
    name = "kotlin-script-dependencies",
    version = 5,
    read = DataInput::readObject,
    write = DataOutput::writeObject
) {
    companion object {
        private const val UNSUPPORTED_FILE_EXT = ".gradle.kts"

        operator fun get(project: Project, file: VirtualFile): ScriptConfigurationSnapshotForFS? {
            if (file.path.endsWith(UNSUPPORTED_FILE_EXT)) return null
            return project.service<ScriptConfigurationSnapshotFile>()[file]
        }

        operator fun set(project: Project, file: VirtualFile, newValue: ScriptConfigurationSnapshotForFS?) {
            if (file.path.endsWith(UNSUPPORTED_FILE_EXT)) return
            project.service<ScriptConfigurationSnapshotFile>()[file] = newValue
        }
    }
}