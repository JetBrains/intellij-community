// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SdkDependency
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.compiledLibraryRoot
import org.jetbrains.kotlin.idea.core.script.getOrCreateLibrary
import org.jetbrains.kotlin.idea.core.script.k2.ScriptClassPathUtil.Companion.findVirtualFiles
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptReportSink
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

open class DefaultScriptConfigurationHandler(
    val project: Project,
    val coroutineScope: CoroutineScope
) : ScriptWorkspaceModelManager, ScriptRefinedConfigurationResolver {
    private val data = ConcurrentHashMap<VirtualFile, ScriptConfigurationWithSdk>()

    override suspend fun create(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptConfigurationWithSdk {
        val current = data[virtualFile]
        if (current != null) return current

        val configuration = resolveScriptConfiguration(virtualFile, definition)
        data[virtualFile] = configuration

        return configuration
    }

    override fun get(virtualFile: VirtualFile): ScriptConfigurationWithSdk? = data[virtualFile]

    private suspend fun resolveScriptConfiguration(virtualFile: VirtualFile, definition: ScriptDefinition): ScriptConfigurationWithSdk {
        val sdk = ProjectRootManager.getInstance(project).projectSdk ?: ProjectJdkTable.getInstance().allJdks.firstOrNull()

        val scriptSource = VirtualFileScriptSource(virtualFile)

        val providedConfiguration = sdk?.homePath?.let {
            definition.compilationConfiguration.with {
                jvm.jdkHome(File(it))
            }
        }

        val result = smartReadAction(project) {
            refineScriptCompilationConfiguration(scriptSource, definition, project, providedConfiguration)
        }

        project.service<ScriptReportSink>().attachReports(virtualFile, result.reports)

        return ScriptConfigurationWithSdk(result, sdk)
    }

    override suspend fun updateWorkspaceModel(configurationPerFile: Map<VirtualFile, ScriptConfigurationWithSdk>) {
        val workspaceModel = project.serviceAsync<WorkspaceModel>()

        workspaceModel.update("updating .kts modules") {
            val entityStorage = getUpdatedStorage(configurationPerFile, workspaceModel)
            it.applyChangesFrom(entityStorage)
        }
    }

    private fun getUpdatedStorage(
        configurations: Map<VirtualFile, ScriptConfigurationWithSdk>,
        workspaceModel: WorkspaceModel,
    ): MutableEntityStorage {
        val result = MutableEntityStorage.from(workspaceModel.currentSnapshot)
        val fileUrlManager = workspaceModel.getVirtualFileUrlManager()

        for ((scriptFile, configurationWithSdk) in configurations) {
            val definition = findScriptDefinition(project, VirtualFileScriptSource(scriptFile))
            val moduleId = getModuleId(project, scriptFile, definition)
            if (result.contains(moduleId)) continue
            val wrapper = configurationWithSdk.scriptConfiguration.valueOrNull() ?: continue

            val source = DefaultScriptEntitySource(scriptFile.toVirtualFileUrl(fileUrlManager))
            val sdkDependency = configurationWithSdk.sdk?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }

            val libraryDependencies = wrapper.configuration?.get(ScriptCompilationConfiguration.dependencies).findVirtualFiles()
                .map { result.getOrCreateLibrary(it.name, listOf(it.compiledLibraryRoot(project)), source) }

            val allDependencies = buildList {
                addIfNotNull(sdkDependency)
                addAll(libraryDependencies)
            }

            result.addEntity(ModuleEntity(moduleId.name, allDependencies, source))
        }

        return result
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): DefaultScriptConfigurationHandler = project.service()
    }

    class DefaultScriptEntitySource(override val virtualFileUrl: VirtualFileUrl?) : KotlinScriptEntitySource(virtualFileUrl)
}