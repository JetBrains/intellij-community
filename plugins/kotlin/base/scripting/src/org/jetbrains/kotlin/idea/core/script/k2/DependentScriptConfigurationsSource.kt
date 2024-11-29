// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.getUpdatedStorage
import org.jetbrains.kotlin.idea.core.script.scriptDefinitionsSourceOfType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import java.nio.file.Path
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm


class DependentScriptConfigurationsSource(override val project: Project, val coroutineScope: CoroutineScope) :
    ScriptConfigurationsSource<BaseScriptModel>(project) {

    private val warmUp by lazy {
        val scriptUrls = ScriptsWithLoadedDependenciesStorage.getInstance(project).state.scripts
        val manager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
        val scripts = scriptUrls.mapNotNull { manager.getOrCreateFromUrl(it).virtualFile }.toSet()

        coroutineScope.launch {
            updateDependenciesAndCreateModules(scripts.map { BaseScriptModel(it) })
        }
    }

    override fun getScriptConfigurations(virtualFile: VirtualFile): ResultWithDiagnostics<ScriptCompilationConfigurationWrapper>? {
        warmUp
        return super.getScriptConfigurations(virtualFile)
    }

    override fun getScriptDefinitionsSource(): ScriptDefinitionsSource? =
        project.scriptDefinitionsSourceOfType<MainKtsScriptDefinitionSource>()

    override suspend fun updateConfigurations(scripts: Iterable<BaseScriptModel>) {
        val projectSdk = ProjectJdkTable.getInstance().allJdks.firstOrNull()

        val configurations = scripts.associate {
            val scriptSource = VirtualFileScriptSource(it.virtualFile)
            val definition = findScriptDefinition(project, scriptSource)

            val javaHome = projectSdk?.homePath

            val providedConfiguration = javaHome?.let {
                definition.compilationConfiguration.with {
                    jvm.jdkHome(File(it))
                }
            }

            it.virtualFile to smartReadAction(project) {
                refineScriptCompilationConfiguration(scriptSource, definition, project, providedConfiguration)
            }
        }

        configurations.forEach { (script, result) ->
            project.service<ScriptReportSink>().attachReports(script, result.reports)
        }

        val scriptConfigurations =
            ScriptConfigurations(configurations, sdks = projectSdk?.homePath?.let { mapOf(Path.of(it) to projectSdk) } ?: emptyMap())

        data.getAndAccumulate(scriptConfigurations) { left, right -> left + right }
        updatePersistentState()
    }

    private fun updatePersistentState() {
        val loadedScripts = data.get().configurations.filter {
            it.value is ResultWithDiagnostics.Success
        }.map {
            it.key.url
        }

        ScriptsWithLoadedDependenciesStorage.getInstance(project).loadState(ScriptsWithLoadedDependenciesState(loadedScripts))
    }

    override suspend fun updateModules(storage: MutableEntityStorage?) {
        val updatedStorage = getUpdatedStorage(
            project, data.get()
        ) { KotlinDependentScriptModuleEntitySource(it) }

        project.workspaceModel.update("Updating MainKts Kotlin Scripts modules") {
            it.replaceBySource(
                { it is KotlinDependentScriptModuleEntitySource }, updatedStorage
            )
        }
    }

    open class KotlinDependentScriptModuleEntitySource(override val virtualFileUrl: VirtualFileUrl?) :
        KotlinScriptEntitySource(virtualFileUrl)
}

@Service(Service.Level.PROJECT)
@State(name = "ScriptWithLoadedDependenciesStorage", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
private class ScriptsWithLoadedDependenciesStorage :
    SimplePersistentStateComponent<ScriptsWithLoadedDependenciesState>(ScriptsWithLoadedDependenciesState()) {

    companion object {
        fun getInstance(project: Project): ScriptsWithLoadedDependenciesStorage = project.service()
    }
}

internal class ScriptsWithLoadedDependenciesState() : BaseState() {
    var scripts by list<String>()

    constructor(scripts: Collection<String>) : this() {
        this.scripts = scripts.toMutableList()
    }
}
