// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptRefinedConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptWorkspaceModelManager
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

open class DefaultScriptConfigurationHandler(
    val project: Project, val coroutineScope: CoroutineScope
) : ScriptWorkspaceModelManager, ScriptRefinedConfigurationResolver {
    private val data = ConcurrentHashMap<VirtualFile, ScriptCompilationConfigurationResult>()

    override suspend fun create(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptCompilationConfigurationResult {
        val current = data[virtualFile]
        if (current != null) return current

        val configuration = resolveScriptConfiguration(virtualFile, definition)
        data[virtualFile] = configuration

        return configuration
    }

    override fun get(virtualFile: VirtualFile): ScriptCompilationConfigurationResult? = data[virtualFile]

    override fun remove(virtualFile: VirtualFile) {
        data.remove(virtualFile)
    }

    private suspend fun resolveScriptConfiguration(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptCompilationConfigurationResult {
        val definitionJdk = definition.compilationConfiguration[ScriptCompilationConfiguration.jvm.jdkHome]
        val configuration = if (definitionJdk != null) definition.compilationConfiguration
        else {
            val projectSdk = ProjectRootManager.getInstance(project).projectSdk?.homePath
            definition.compilationConfiguration.with {
                projectSdk?.let {
                    jvm.jdkHome(File(it))
                }
            }
        }

        val scriptSource = VirtualFileScriptSource(virtualFile)

        val result = smartReadAction(project) {
            refineScriptCompilationConfiguration(scriptSource, definition, project, configuration)
        }

        project.service<ScriptReportSink>().attachReports(virtualFile, result.reports)

        return result
    }

    override suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptCompilationConfigurationResult>) {
        val workspaceModel = project.serviceAsync<WorkspaceModel>()

        workspaceModel.update("updating .kts modules") {
            val entityStorage = getUpdatedStorage(configurationPerFile, workspaceModel)
            it.applyChangesFrom(entityStorage)
        }
    }

    private fun getUpdatedStorage(
        configurations: Map<VirtualFile, ScriptCompilationConfigurationResult>,
        workspaceModel: WorkspaceModel,
    ): MutableEntityStorage {
        val storage = workspaceModel.currentSnapshot
        val result = MutableEntityStorage.from(storage)
        val index = storage.getVirtualFileUrlIndex()

        val fileUrlManager = workspaceModel.getVirtualFileUrlManager()

        for ((scriptFile, configurationResult) in configurations) {
            val scriptUrl = scriptFile.toVirtualFileUrl(fileUrlManager)
            if (index.findEntitiesByUrl(scriptUrl).any()) continue

            val definition = findScriptDefinition(project, VirtualFileScriptSource(scriptFile))
            val configuration = configurationResult.valueOrNull()?.configuration ?: continue

            val libraryIds = generateScriptLibraryEntities(configuration, definition, project)
            libraryIds.filterNot {
                result.contains(it)
            }.forEach { (classes, sources) ->
                result addEntity KotlinScriptLibraryEntity(classes, sources, DefaultScriptEntitySource)
            }

            result addEntity KotlinScriptEntity(
                scriptUrl, libraryIds.toList(), DefaultScriptEntitySource
            ) {
                this.sdkId = configuration.sdkId
            }
        }

        return result
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): DefaultScriptConfigurationHandler = project.service()
    }

    object DefaultScriptEntitySource : EntitySource
}