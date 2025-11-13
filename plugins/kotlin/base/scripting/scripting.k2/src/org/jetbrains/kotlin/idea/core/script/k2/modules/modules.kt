// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptDiagnostic.Severity
import kotlin.script.experimental.api.SourceCode

interface ScriptWorkspaceModelManager {
    suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptCompilationConfigurationResult>) {}
}

@Service(Service.Level.PROJECT)
class KotlinScriptModuleManager(private val project: Project, private val coroutineScope: CoroutineScope) {
    fun removeScriptModules(scripts: List<VirtualFile>) {
        coroutineScope.launch {
            project.removeScriptModules(scripts)
        }
    }

    companion object {
        suspend fun Project.removeScriptModules(scripts: List<VirtualFile>) {
            val currentSnapshot = workspaceModel.currentSnapshot
            val fileUrlManager = workspaceModel.getVirtualFileUrlManager()

            val modulesToRemove = scripts.flatMap {
                currentSnapshot.getVirtualFileUrlIndex().findEntitiesByUrl(it.toVirtualFileUrl(fileUrlManager))
            }

            if (modulesToRemove.isEmpty()) return

            workspaceModel.update("removing .kts modules") {
                modulesToRemove.forEach(it::removeEntity)
            }
        }

        @JvmStatic
        fun getInstance(project: Project): KotlinScriptModuleManager = project.service()
    }
}


data class ScriptingHostConfigurationEntity(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScriptingHostConfigurationEntity

        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()
}

data class ScriptEvaluationConfigurationEntity(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScriptEvaluationConfigurationEntity

        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()
}

data class ScriptCompilationConfigurationEntity(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScriptCompilationConfigurationEntity

        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()
}

data class ScriptDiagnosticData(
    val code: Int,
    val message: String,
    val severity: Severity = Severity.ERROR,
    val sourcePath: String? = null,
    val location: SourceCode.Location? = null,
    val exceptionMessage: String? = null
) {
    fun toScriptDiagnostic(): ScriptDiagnostic = ScriptDiagnostic(
        code, message, severity, sourcePath, location, Throwable(exceptionMessage)
    )
}

fun ScriptDiagnostic.toData(): ScriptDiagnosticData =
    ScriptDiagnosticData(code, message, severity, sourcePath, location, exception?.message)
