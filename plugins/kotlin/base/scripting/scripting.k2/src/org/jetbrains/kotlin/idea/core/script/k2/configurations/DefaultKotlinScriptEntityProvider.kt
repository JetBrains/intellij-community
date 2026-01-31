// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.core.script.k2.asEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.modifyKotlinScriptLibraryEntity
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptDiagnostic.Companion.unspecifiedError
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

@Service(Service.Level.PROJECT)
class DefaultKotlinScriptEntityProvider(
    override val project: Project, val coroutineScope: CoroutineScope
) : KotlinScriptEntityProvider(project) {
    override suspend fun updateWorkspaceModel(
        virtualFile: VirtualFile, definition: ScriptDefinition
    ) {
        val configuration = definition.getInitialConfiguration()
        val scriptSource = VirtualFileScriptSource(virtualFile)
        val scriptUrl = virtualFile.virtualFileUrl
        if (project.workspaceModel.currentSnapshot.containsScriptEntity(scriptUrl)) return

        val result = smartReadAction(project) {
            try {
                refineScriptCompilationConfiguration(scriptSource, definition, project, configuration)
            } catch (e: Throwable) {
                ResultWithDiagnostics.Failure(
                    ScriptDiagnostic(
                        code = unspecifiedError, exception = e, message = "Failed to refine script", sourcePath = scriptSource.locationId
                    )
                )
            }
        }

        fun updateStorage(storage: MutableEntityStorage) {
            val configuration = result.valueOrNull()?.configuration ?: return
            val definition = findScriptDefinition(project, VirtualFileScriptSource(virtualFile))

            val libraryIds = generateScriptLibraryEntities(project, configuration, definition).toList()
            for ((id, sources) in libraryIds) {
                val existingLibrary = storage.resolve(id)
                if (existingLibrary == null) {
                    storage addEntity KotlinScriptLibraryEntity(id.classes, setOf(scriptUrl), DefaultScriptEntitySource) {
                        this.sources += sources
                    }
                } else {
                    storage.modifyKotlinScriptLibraryEntity(existingLibrary) {
                        this.sources += sources
                        this.usedInScripts += scriptUrl
                    }
                }
            }

            storage addEntity KotlinScriptEntity(
                scriptUrl, libraryIds.map { it.first }, DefaultScriptEntitySource
            ) {
                this.configuration = configuration.asEntity()
                this.sdkId = configuration.sdkId
            }
        }

        project.updateKotlinScriptEntities(DefaultScriptEntitySource) {
            if (!it.containsScriptEntity(scriptUrl)) {
                updateStorage(it)
            }
        }

        project.service<ScriptReportSink>().attachReports(virtualFile, result.reports)
    }

    private fun ScriptDefinition.getInitialConfiguration(): ScriptCompilationConfiguration {
        val definitionJdk = compilationConfiguration[ScriptCompilationConfiguration.jvm.jdkHome]

        return if (definitionJdk != null) compilationConfiguration
        else {
            val projectSdk = ProjectRootManager.getInstance(project).projectSdk?.homePath
            compilationConfiguration.with {
                projectSdk?.let {
                    jvm.jdkHome(File(it))
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): DefaultKotlinScriptEntityProvider = project.service()
    }
}

object DefaultScriptEntitySource : EntitySource
